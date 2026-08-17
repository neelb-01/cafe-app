package com.adl.cafe.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adl.cafe.data.CafeRepository
import com.adl.cafe.data.Pricing
import com.adl.cafe.data.model.OrderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CheckoutTotals(
    val itemCount: Int = 0,
    val subtotalCents: Int = 0,
    val taxCents: Int = 0,
    val totalCents: Int = 0
)

class CheckoutViewModel(private val repository: CafeRepository) : ViewModel() {

    val totals: StateFlow<CheckoutTotals> = repository.observeCartLines()
        .map { lines ->
            val subtotal = lines.sumOf { it.lineTotalCents }
            val tax = Pricing.taxOn(subtotal)
            CheckoutTotals(
                itemCount = lines.sumOf { it.cartItem.quantity },
                subtotalCents = subtotal,
                taxCents = tax,
                totalCents = subtotal + tax
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CheckoutTotals()
        )

    private val _placing = MutableStateFlow(false)
    val placing: StateFlow<Boolean> = _placing.asStateFlow()

    /**
     * Single source of truth for the button's enabled state — two separate
     * collectors writing it would race with each other.
     */
    val canPlaceOrder: StateFlow<Boolean> = combine(totals, placing) { totals, placing ->
        totals.itemCount > 0 && !placing
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    private val _nameError = MutableStateFlow(false)
    val nameError: StateFlow<Boolean> = _nameError.asStateFlow()

    /** Emits the new order id once, for the fragment to navigate on. */
    private val _placedOrderId = MutableStateFlow<Long?>(null)
    val placedOrderId: StateFlow<Long?> = _placedOrderId.asStateFlow()

    fun placeOrder(name: String, orderType: OrderType, note: String) {
        if (name.isBlank()) {
            _nameError.value = true
            return
        }
        if (_placing.value) return

        _nameError.value = false
        _placing.value = true
        viewModelScope.launch {
            val orderId = repository.placeOrder(name, orderType, note)
            _placing.value = false
            _placedOrderId.value = orderId
        }
    }

    fun clearNameError() {
        _nameError.value = false
    }

    fun consumePlacedOrder() {
        _placedOrderId.value = null
    }
}
