package com.adl.cafe.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.adl.cafe.R
import com.adl.cafe.data.model.Suggestion
import com.adl.cafe.databinding.ItemRecommendationBinding
import com.adl.cafe.util.asMoney

/** A suggestion card under an assistant reply, and whether it is already in the cart. */
data class SuggestionCard(val position: Int, val suggestion: Suggestion, val added: Boolean)

/** The cards under one assistant reply; same card as the recommendation rows. */
class SuggestionAdapter(
    private val onAdd: (position: Int) -> Unit
) : ListAdapter<SuggestionCard, SuggestionAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(
        private val binding: ItemRecommendationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(card: SuggestionCard) = with(binding) {
            val context = root.context
            val suggestion = card.suggestion
            val item = suggestion.item
            textEmoji.text = item.emoji
            textName.text = if (suggestion.quantity > 1) {
                context.getString(R.string.chat_suggestion_quantity, suggestion.quantity, item.name)
            } else {
                item.name
            }
            val price = suggestion.unitPriceCents.asMoney()
            textPrice.text = if (item.sizable) {
                context.getString(R.string.chat_suggestion_size_price, suggestion.size.label, price)
            } else {
                price
            }

            buttonAdd.isEnabled = !card.added
            buttonAdd.setIconResource(if (card.added) R.drawable.ic_check else R.drawable.ic_add)
            buttonAdd.contentDescription = context.getString(
                if (card.added) R.string.chat_added else R.string.add_item,
                item.name
            )
            buttonAdd.setOnClickListener { onAdd(card.position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecommendationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SuggestionCard>() {
            override fun areItemsTheSame(old: SuggestionCard, new: SuggestionCard) =
                old.position == new.position
            override fun areContentsTheSame(old: SuggestionCard, new: SuggestionCard) = old == new
        }
    }
}
