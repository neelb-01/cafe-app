package com.adl.cafe.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderNumber: String = "",
    val placedAtMillis: Long,
    val customerName: String,
    val orderType: OrderType,
    val note: String,
    val subtotalCents: Int,
    val taxCents: Int,
    val totalCents: Int,
    val status: OrderStatus = OrderStatus.PLACED
)

/**
 * Order lines snapshot name/price at the time of ordering, so editing or
 * removing a menu item later never rewrites somebody's receipt.
 */
@Entity(
    tableName = "order_lines",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("orderId")]
)
data class OrderLine(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: Long,
    val itemName: String,
    val emoji: String,
    val size: DrinkSize,
    val sizable: Boolean,
    val quantity: Int,
    val unitPriceCents: Int
) {
    val lineTotalCents: Int get() = unitPriceCents * quantity
}

data class OrderWithLines(
    @Embedded val order: OrderEntity,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val lines: List<OrderLine>
) {
    val itemCount: Int get() = lines.sumOf { it.quantity }
}
