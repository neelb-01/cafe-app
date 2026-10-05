package com.adl.cafe.data

import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.data.model.OrderWithLines
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.pow

/**
 * On-device menu recommendations, learned from this device's own order history.
 * Holds no Android types, so it is tested on the plain JVM like [Pricing].
 *
 * Every signal is a weighted count of past orders, decayed by age so tastes can
 * change. An order counts once per item it contains, whatever the quantity, so
 * a single office run of six lattes does not outweigh a daily habit.
 *
 * History is matched to the menu **by name**: [com.adl.cafe.data.model.OrderLine]
 * snapshots the name rather than pointing at a menu row, so an item renamed on
 * the server starts with no history. Items no longer on the menu are ignored.
 *
 * Scores are Doubles because they only rank; none of this touches a price.
 */
object Recommender {

    /** An order this many days old counts half as much as one placed today. */
    const val HALF_LIFE_DAYS = 14.0

    // "For you": what you usually have, what you have at this time of day.
    private const val W_PERSONAL = 1.0
    private const val W_DAYPART = 0.5
    private const val W_POPULAR = 0.25

    // "Goes well with": what you have had alongside the cart, then drink <-> food.
    private const val W_PAIR = 1.0
    private const val W_COMPLEMENT = 0.6
    private const val W_PAIR_PERSONAL = 0.3
    private const val W_PAIR_POPULAR = 0.15

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000.0

    /**
     * Items for the menu's "For you" row, best first. Empty until the first
     * order: with no history this would only repeat the Popular items, which
     * already lead the menu.
     */
    fun forYou(
        menu: List<MenuItem>,
        history: List<OrderWithLines>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        limit: Int = 6
    ): List<MenuItem> {
        if (history.isEmpty()) return emptyList()

        val byName = menu.associateBy { it.name }
        val affinity = affinity(history, byName, nowMillis)
        val maxAffinity = affinity.values.maxOrNull() ?: 0.0

        // Share of this daypart's orders that included each category.
        val daypart = daypartOf(nowMillis, timeZone)
        val categoryWeight = HashMap<String, Double>()
        var daypartTotal = 0.0
        for (order in history) {
            if (daypartOf(order.order.placedAtMillis, timeZone) != daypart) continue
            val w = decay(order.order.placedAtMillis, nowMillis)
            val categories = order.lines.mapNotNullTo(HashSet()) { byName[it.itemName]?.category }
            if (categories.isEmpty()) continue
            daypartTotal += w
            for (category in categories) categoryWeight.merge(category, w, Double::plus)
        }

        return rank(menu, limit) { item ->
            val personal = if (maxAffinity > 0) (affinity[item.name] ?: 0.0) / maxAffinity else 0.0
            val share = if (daypartTotal > 0) (categoryWeight[item.category] ?: 0.0) / daypartTotal else 0.0
            W_PERSONAL * personal + W_DAYPART * share + W_POPULAR * item.isPopular.toScore()
        }
    }

    /**
     * Items for the cart's "Goes well with" row, best first, never including
     * anything already in the cart. Works before the first order: a cart of
     * only drinks suggests food, and vice versa.
     */
    fun goesWellWith(
        menu: List<MenuItem>,
        history: List<OrderWithLines>,
        cartMenuItemIds: Set<Long>,
        nowMillis: Long,
        limit: Int = 4
    ): List<MenuItem> {
        val inCart = menu.filter { it.id in cartMenuItemIds }
        if (inCart.isEmpty()) return emptyList()

        val byName = menu.associateBy { it.name }
        val cartNames = inCart.mapTo(HashSet()) { it.name }

        val affinity = affinity(history, byName, nowMillis)
        val maxAffinity = affinity.values.maxOrNull() ?: 0.0

        // Past orders that shared at least one item with the cart.
        val pairing = HashMap<String, Double>()
        for (order in history) {
            val names = order.lines.mapTo(HashSet()) { it.itemName }
            if (names.none { it in cartNames }) continue
            val w = decay(order.order.placedAtMillis, nowMillis)
            for (name in names) if (name !in cartNames) pairing.merge(name, w, Double::plus)
        }
        val maxPairing = pairing.values.maxOrNull() ?: 0.0

        // `sizable` is the menu's own drink/food split, so no category names are hardcoded.
        val cartHasDrink = inCart.any { it.sizable }
        val cartHasFood = inCart.any { !it.sizable }

        return rank(menu.filter { it.id !in cartMenuItemIds }, limit) { item ->
            val pair = if (maxPairing > 0) (pairing[item.name] ?: 0.0) / maxPairing else 0.0
            val personal = if (maxAffinity > 0) (affinity[item.name] ?: 0.0) / maxAffinity else 0.0
            val complements = (cartHasDrink && !cartHasFood && !item.sizable) ||
                (cartHasFood && !cartHasDrink && item.sizable)
            W_PAIR * pair +
                W_COMPLEMENT * complements.toScore() +
                W_PAIR_PERSONAL * personal +
                W_PAIR_POPULAR * item.isPopular.toScore()
        }
    }

    /** Decayed count of orders containing each item still on the menu. */
    private fun affinity(
        history: List<OrderWithLines>,
        byName: Map<String, MenuItem>,
        nowMillis: Long
    ): Map<String, Double> {
        val result = HashMap<String, Double>()
        for (order in history) {
            val w = decay(order.order.placedAtMillis, nowMillis)
            order.lines.mapTo(HashSet()) { it.itemName }
                .filter { it in byName }
                .forEach { result.merge(it, w, Double::plus) }
        }
        return result
    }

    /** Positive scores only, best first; ties break by name so the row is stable. */
    private inline fun rank(
        candidates: List<MenuItem>,
        limit: Int,
        score: (MenuItem) -> Double
    ): List<MenuItem> = candidates
        .filter { it.isAvailable }
        .map { it to score(it) }
        .filter { it.second > 0.0 }
        .sortedWith(compareByDescending<Pair<MenuItem, Double>> { it.second }.thenBy { it.first.name })
        .take(limit)
        .map { it.first }

    /** Halves every [HALF_LIFE_DAYS]. A clock that has gone backwards counts as today. */
    private fun decay(placedAtMillis: Long, nowMillis: Long): Double {
        val ageDays = (nowMillis - placedAtMillis).coerceAtLeast(0) / DAY_MILLIS
        return 0.5.pow(ageDays / HALF_LIFE_DAYS)
    }

    internal enum class Daypart { MORNING, AFTERNOON, EVENING }

    /** Calendar rather than java.time, which needs API 26 without desugaring. */
    internal fun daypartOf(millis: Long, timeZone: TimeZone): Daypart {
        val hour = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
            .get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> Daypart.MORNING
            in 11..16 -> Daypart.AFTERNOON
            else -> Daypart.EVENING
        }
    }

    private fun Boolean.toScore(): Double = if (this) 1.0 else 0.0
}
