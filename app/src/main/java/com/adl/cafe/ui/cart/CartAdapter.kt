package com.adl.cafe.ui.cart

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.adl.cafe.R
import com.adl.cafe.data.model.CartLine
import com.adl.cafe.databinding.ItemCartBinding
import com.adl.cafe.util.asMoney

class CartAdapter(
    private val onIncrement: (CartLine) -> Unit,
    private val onDecrement: (CartLine) -> Unit,
    private val onRemove: (CartLine) -> Unit
) : ListAdapter<CartLine, CartAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(
        private val binding: ItemCartBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(line: CartLine) = with(binding) {
            textEmoji.text = line.menuItem.emoji
            textName.text = line.menuItem.name
            textQuantity.text = line.cartItem.quantity.toString()
            textLineTotal.text = line.lineTotalCents.asMoney()

            // Food items have no size to show, just the unit price.
            textSize.text = if (line.menuItem.sizable) {
                root.context.getString(
                    R.string.size_and_unit_price,
                    line.cartItem.size.label,
                    line.unitPriceCents.asMoney()
                )
            } else {
                line.unitPriceCents.asMoney()
            }

            buttonIncrement.setOnClickListener { onIncrement(line) }
            buttonDecrement.setOnClickListener { onDecrement(line) }
            buttonRemove.setOnClickListener { onRemove(line) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CartLine>() {
            override fun areItemsTheSame(old: CartLine, new: CartLine) =
                old.cartItem.id == new.cartItem.id

            override fun areContentsTheSame(old: CartLine, new: CartLine) = old == new
        }
    }
}
