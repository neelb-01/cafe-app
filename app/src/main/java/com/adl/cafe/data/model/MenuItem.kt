package com.adl.cafe.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu_items")
data class MenuItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val category: String,
    /** Base price in cents, so we never do money math in floating point. */
    val priceCents: Int,
    /** Stand-in for a product photo — keeps the project asset-free. */
    val emoji: String,
    /** Drinks can be resized; food is always served one way. */
    val sizable: Boolean = true,
    val isPopular: Boolean = false,
    val isAvailable: Boolean = true
)
