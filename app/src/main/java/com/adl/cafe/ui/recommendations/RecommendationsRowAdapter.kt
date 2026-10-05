package com.adl.cafe.ui.recommendations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.databinding.ItemRecommendationsRowBinding

/**
 * A titled, horizontally scrolling row of recommendations, as a single-item
 * adapter so a screen can put it in a ConcatAdapter and have it scroll with
 * its list. Shows nothing (zero items) when there is nothing to recommend.
 */
class RecommendationsRowAdapter(
    @StringRes private val titleRes: Int,
    private val onClick: (MenuItem) -> Unit,
    private val onQuickAdd: (MenuItem) -> Unit
) : RecyclerView.Adapter<RecommendationsRowAdapter.ViewHolder>() {

    private var items: List<MenuItem> = emptyList()

    fun submitList(newItems: List<MenuItem>) {
        // Unchanged suggestions must not rebind, or the row would jump back to
        // its start under someone mid-swipe.
        if (newItems == items) return
        val wasShown = items.isNotEmpty()
        items = newItems
        when {
            // The payload keeps the same holder, so the inner row diffs in place
            // instead of the whole row being cross-faded.
            wasShown && newItems.isNotEmpty() -> notifyItemChanged(0, ITEMS_CHANGED)
            wasShown -> notifyItemRemoved(0)
            newItems.isNotEmpty() -> notifyItemInserted(0)
        }
    }

    inner class ViewHolder(
        private val binding: ItemRecommendationsRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val cards = RecommendationAdapter(onClick, onQuickAdd)

        init {
            binding.textTitle.setText(titleRes)
            binding.recyclerRecommendations.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = cards
            }
        }

        /**
         * New suggestions start from the first card. Left alone, the row keeps its
         * sideways offset, so a new top pick lands off the left edge; and a row
         * that is hidden and shown again would come back mid-scroll.
         */
        fun bind(items: List<MenuItem>) = cards.submitList(items) {
            binding.recyclerRecommendations.scrollToPosition(0)
        }
    }

    override fun getItemCount() = if (items.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecommendationsRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items)

    private companion object {
        val ITEMS_CHANGED = Any()
    }
}
