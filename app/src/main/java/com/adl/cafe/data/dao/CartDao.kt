package com.adl.cafe.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.adl.cafe.data.model.CartItem
import com.adl.cafe.data.model.CartLine
import com.adl.cafe.data.model.DrinkSize
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {

    @Transaction
    @Query("SELECT * FROM cart_items ORDER BY id ASC")
    fun observeCartLines(): Flow<List<CartLine>>

    @Transaction
    @Query("SELECT * FROM cart_items ORDER BY id ASC")
    suspend fun getCartLines(): List<CartLine>

    /** Drives the bottom-nav badge. COALESCE keeps it 0 rather than null on an empty cart. */
    @Query("SELECT COALESCE(SUM(quantity), 0) FROM cart_items")
    fun observeCartCount(): Flow<Int>

    @Query("SELECT * FROM cart_items WHERE menuItemId = :menuItemId AND size = :size LIMIT 1")
    suspend fun findLine(menuItemId: Long, size: DrinkSize): CartItem?

    @Insert
    suspend fun insert(item: CartItem): Long

    @Update
    suspend fun update(item: CartItem)

    @Delete
    suspend fun delete(item: CartItem)

    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cart_items")
    suspend fun clear()
}
