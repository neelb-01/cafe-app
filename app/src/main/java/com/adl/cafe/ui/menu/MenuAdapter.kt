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

/** A menu row and how many of it are in the cart (all sizes), which picks Add or the stepper. */
data class MenuRow(val item: MenuItem, val cartQuantity: Int)

class MenuAdapter(
    private val onClick: (MenuItem) -> Unit,
    private val onQuickAdd: (MenuItem) -> Unit,
    private val onIncrement: (MenuItem) -> Unit,
    private val onDecrement: (MenuItem) -> Unit,
    private val onEmojiClick: (MenuItem) -> Unit
) : ListAdapter<MenuRow, MenuAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(
        private val binding: ItemMenuBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: MenuRow) = with(binding) {
            val item = row.item
            textEmoji.text = item.emoji
            textName.text = item.name
            textDescription.text = item.description
            textPrice.text = item.priceCents.asMoney()
            chipPopular.visibility = if (item.isPopular) View.VISIBLE else View.GONE

            val inCart = row.cartQuantity > 0
            buttonAdd.visibility = if (inCart) View.GONE else View.VISIBLE
            stepper.visibility = if (inCart) View.VISIBLE else View.GONE
            textQuantity.text = row.cartQuantity.toString()

            root.setOnClickListener { onClick(item) }
            buttonAdd.setOnClickListener { onQuickAdd(item) }
            buttonIncrement.setOnClickListener { onIncrement(item) }
            buttonDecrement.setOnClickListener { onDecrement(item) }
            textEmoji.setOnClickListener { onEmojiClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<MenuRow>() {
            override fun areItemsTheSame(old: MenuRow, new: MenuRow) = old.item.id == new.item.id
            override fun areContentsTheSame(old: MenuRow, new: MenuRow) = old == new
        }
    }
}
