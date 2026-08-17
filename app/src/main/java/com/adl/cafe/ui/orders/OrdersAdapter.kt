package com.adl.cafe.ui.orders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.adl.cafe.R
import com.adl.cafe.data.model.OrderWithLines
import com.adl.cafe.databinding.ItemOrderBinding
import com.adl.cafe.util.asMoney
import com.adl.cafe.util.asOrderDate

class OrdersAdapter : ListAdapter<OrderWithLines, OrdersAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(
        private val binding: ItemOrderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(order: OrderWithLines) = with(binding) {
            textOrderNumber.text = order.order.orderNumber
            textDate.text = order.order.placedAtMillis.asOrderDate()
            textTotal.text = order.order.totalCents.asMoney()
            textStatus.text = order.order.status.label
            textOrderType.text = order.order.orderType.label

            textItems.text = order.lines.joinToString(separator = "\n") { line ->
                val name = if (line.sizable) {
                    "${line.size.label} ${line.itemName}"
                } else {
                    line.itemName
                }
                root.context.getString(
                    R.string.order_line_summary,
                    line.quantity,
                    name,
                    line.lineTotalCents.asMoney()
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<OrderWithLines>() {
            override fun areItemsTheSame(old: OrderWithLines, new: OrderWithLines) =
                old.order.id == new.order.id

            override fun areContentsTheSame(old: OrderWithLines, new: OrderWithLines) = old == new
        }
    }
}
