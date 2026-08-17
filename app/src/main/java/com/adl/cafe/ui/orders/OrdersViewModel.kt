package com.adl.cafe.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adl.cafe.data.CafeRepository
import com.adl.cafe.data.model.OrderWithLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class OrdersViewModel(private val repository: CafeRepository) : ViewModel() {

    val orders: StateFlow<List<OrderWithLines>> = repository.observeOrders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun observeOrder(orderId: Long): Flow<OrderWithLines?> = repository.observeOrder(orderId)
}
