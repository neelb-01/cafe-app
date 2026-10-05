package com.adl.cafe.ui.recommendations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.adl.cafe.R
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.databinding.ItemRecommendationBinding
import com.adl.cafe.util.asMoney

/** The cards inside a [RecommendationsRowAdapter] row. */
class RecommendationAdapter(
    private val onClick: (MenuItem) -> Unit,
    private val onQuickAdd: (MenuItem) -> Unit
) : ListAdapter<MenuItem, RecommendationAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(
        private val binding: ItemRecommendationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MenuItem) = with(binding) {
            textEmoji.text = item.emoji
            textName.text = item.name
            textPrice.text = item.priceCents.asMoney()
            buttonAdd.contentDescription = root.context.getString(R.string.add_item, item.name)

            root.setOnClickListener { onClick(item) }
            buttonAdd.setOnClickListener { onQuickAdd(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecommendationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<MenuItem>() {
            override fun areItemsTheSame(old: MenuItem, new: MenuItem) = old.id == new.id
            override fun areContentsTheSame(old: MenuItem, new: MenuItem) = old == new
        }
    }
}
