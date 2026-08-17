package com.adl.cafe.data

import com.adl.cafe.data.model.MenuItem

/** The menu the database is populated with the first time the app runs. */
object SeedData {

    const val CATEGORY_COFFEE = "Coffee"
    const val CATEGORY_ESPRESSO = "Espresso"
    const val CATEGORY_TEA = "Tea"
    const val CATEGORY_COLD = "Cold Brew"
    const val CATEGORY_PASTRIES = "Pastries"
    const val CATEGORY_FOOD = "Food"

    val menu: List<MenuItem> = listOf(
        MenuItem(
            name = "House Drip",
            description = "Our daily rotating single origin, brewed batch-style.",
            category = CATEGORY_COFFEE,
            priceCents = 320,
            emoji = "☕",
            isPopular = true
        ),
        MenuItem(
            name = "Pour Over",
            description = "Hand poured to order over a V60. Takes about four minutes.",
            category = CATEGORY_COFFEE,
            priceCents = 480,
            emoji = "☕"
        ),
        MenuItem(
            name = "Americano",
            description = "Double shot cut with hot water.",
            category = CATEGORY_ESPRESSO,
            priceCents = 350,
            emoji = "☕"
        ),
        MenuItem(
            name = "Latte",
            description = "Double shot with steamed milk and a thin cap of foam.",
            category = CATEGORY_ESPRESSO,
            priceCents = 450,
            emoji = "🥛",
            isPopular = true
        ),
        MenuItem(
            name = "Cappuccino",
            description = "Equal parts espresso, steamed milk and foam.",
            category = CATEGORY_ESPRESSO,
            priceCents = 430,
            emoji = "☕"
        ),
        MenuItem(
            name = "Flat White",
            description = "Ristretto shots with velvety microfoam.",
            category = CATEGORY_ESPRESSO,
            priceCents = 460,
            emoji = "🥛"
        ),
        MenuItem(
            name = "Mocha",
            description = "Espresso, dark chocolate and steamed milk.",
            category = CATEGORY_ESPRESSO,
            priceCents = 495,
            emoji = "🍫"
        ),
        MenuItem(
            name = "Cortado",
            description = "Espresso cut with an equal measure of warm milk.",
            category = CATEGORY_ESPRESSO,
            priceCents = 400,
            emoji = "☕"
        ),
        MenuItem(
            name = "Cold Brew",
            description = "Steeped eighteen hours, served over ice.",
            category = CATEGORY_COLD,
            priceCents = 460,
            emoji = "🧊",
            isPopular = true
        ),
        MenuItem(
            name = "Nitro Cold Brew",
            description = "Nitrogen infused for a soft, cascading pour.",
            category = CATEGORY_COLD,
            priceCents = 540,
            emoji = "🧊"
        ),
        MenuItem(
            name = "Iced Latte",
            description = "Double shot poured over cold milk and ice.",
            category = CATEGORY_COLD,
            priceCents = 470,
            emoji = "🧋"
        ),
        MenuItem(
            name = "Earl Grey",
            description = "Ceylon black tea with bergamot.",
            category = CATEGORY_TEA,
            priceCents = 300,
            emoji = "🍵"
        ),
        MenuItem(
            name = "Green Sencha",
            description = "Grassy Japanese green tea, brewed at 80°C.",
            category = CATEGORY_TEA,
            priceCents = 320,
            emoji = "🍵"
        ),
        MenuItem(
            name = "Masala Chai",
            description = "Black tea simmered with cardamom, ginger and clove.",
            category = CATEGORY_TEA,
            priceCents = 380,
            emoji = "🫖",
            isPopular = true
        ),
        MenuItem(
            name = "Butter Croissant",
            description = "Laminated overnight, baked each morning.",
            category = CATEGORY_PASTRIES,
            priceCents = 340,
            emoji = "🥐",
            sizable = false,
            isPopular = true
        ),
        MenuItem(
            name = "Pain au Chocolat",
            description = "Two batons of dark chocolate in butter pastry.",
            category = CATEGORY_PASTRIES,
            priceCents = 375,
            emoji = "🥐",
            sizable = false
        ),
        MenuItem(
            name = "Blueberry Muffin",
            description = "Sour cream crumb muffin packed with berries.",
            category = CATEGORY_PASTRIES,
            priceCents = 330,
            emoji = "🧁",
            sizable = false
        ),
        MenuItem(
            name = "Cinnamon Roll",
            description = "Soft brioche roll with cream cheese glaze.",
            category = CATEGORY_PASTRIES,
            priceCents = 420,
            emoji = "🍥",
            sizable = false
        ),
        MenuItem(
            name = "Avocado Toast",
            description = "Sourdough, smashed avocado, chilli and lemon.",
            category = CATEGORY_FOOD,
            priceCents = 780,
            emoji = "🥑",
            sizable = false
        ),
        MenuItem(
            name = "Halloumi Sandwich",
            description = "Grilled halloumi, roast pepper and rocket in ciabatta.",
            category = CATEGORY_FOOD,
            priceCents = 890,
            emoji = "🥪",
            sizable = false
        ),
        MenuItem(
            name = "Breakfast Bagel",
            description = "Egg, cheddar and smoked paprika mayo.",
            category = CATEGORY_FOOD,
            priceCents = 820,
            emoji = "🥯",
            sizable = false
        )
    )
}
