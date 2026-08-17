package com.adl.cafe.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A line in the (single, always-open) cart. The same menu item at two different
 * sizes is two rows, which is why the unique index spans both columns.
 */
@Entity(
    tableName = "cart_items",
    foreignKeys = [
        ForeignKey(
            entity = MenuItem::class,
            parentColumns = ["id"],
            childColumns = ["menuItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["menuItemId", "size"], unique = true)]
)
data class CartItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val menuItemId: Long,
    val size: DrinkSize,
    val quantity: Int
)

/** Cart row joined with the menu item it points at. */
data class CartLine(
    @Embedded val cartItem: CartItem,
    @Relation(parentColumn = "menuItemId", entityColumn = "id")
    val menuItem: MenuItem
) {
    val unitPriceCents: Int get() = menuItem.priceCents + cartItem.size.surchargeCents
    val lineTotalCents: Int get() = unitPriceCents * cartItem.quantity
}
