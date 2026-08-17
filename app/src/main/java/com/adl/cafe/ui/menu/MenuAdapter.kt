package com.adl.cafe.ui.menu

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.databinding.ItemMenuBinding
import com.adl.cafe.util.asMoney

class MenuAdapter(
    private val onClick: (MenuItem) -> Unit,
    private val onQuickAdd: (MenuItem) -> Unit
) : ListAdapter<MenuItem, MenuAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(
        private val binding: ItemMenuBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MenuItem) = with(binding) {
            textEmoji.text = item.emoji
            textName.text = item.name
            textDescription.text = item.description
            textPrice.text = item.priceCents.asMoney()
            chipPopular.visibility = if (item.isPopular) View.VISIBLE else View.GONE

            root.setOnClickListener { onClick(item) }
            buttonAdd.setOnClickListener { onQuickAdd(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
