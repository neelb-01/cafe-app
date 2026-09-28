package com.adl.cafe.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.adl.cafe.data.model.OrderEntity
import com.adl.cafe.data.model.OrderLine
import com.adl.cafe.data.model.OrderWithLines
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Insert
    suspend fun insertOrder(order: OrderEntity): Long

    @Insert
    suspend fun insertLines(lines: List<OrderLine>)

    @Transaction
    @Query("SELECT * FROM orders ORDER BY placedAtMillis DESC")
    fun observeOrders(): Flow<List<OrderWithLines>>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :orderId")
    fun observeOrder(orderId: Long): Flow<OrderWithLines?>
}
