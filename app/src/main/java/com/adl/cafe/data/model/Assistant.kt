package com.adl.cafe.data.model

/** One reply from the menu assistant, with its suggestions matched to the local menu. */
data class AssistantReply(
    val text: String,
    val suggestions: List<Suggestion>
)

/** An item the assistant proposes adding, at a size and quantity. */
data class Suggestion(
    val item: MenuItem,
    val size: DrinkSize,
    val quantity: Int
) {
    val unitPriceCents: Int get() = item.priceCents + size.surchargeCents
}
