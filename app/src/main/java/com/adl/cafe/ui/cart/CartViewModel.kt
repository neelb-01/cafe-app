package com.adl.cafe.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adl.cafe.data.CafeRepository
import com.adl.cafe.data.Pricing
import com.adl.cafe.data.model.CartLine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CartUiState(
    val lines: List<CartLine> = emptyList(),
    val subtotalCents: Int = 0,
    val taxCents: Int = 0,
    val totalCents: Int = 0
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

class CartViewModel(private val repository: CafeRepository) : ViewModel() {

    val state: StateFlow<CartUiState> = repository.observeCartLines()
        .map { lines ->
            val subtotal = lines.sumOf { it.lineTotalCents }
            val tax = Pricing.taxOn(subtotal)
            CartUiState(
                lines = lines,
                subtotalCents = subtotal,
                taxCents = tax,
                totalCents = subtotal + tax
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CartUiState()
        )

    fun increment(line: CartLine) = setQuantity(line, line.cartItem.quantity + 1)

    fun decrement(line: CartLine) = setQuantity(line, line.cartItem.quantity - 1)

    fun remove(line: CartLine) {
        viewModelScope.launch { repository.removeFromCart(line.cartItem.id) }
    }

    fun clear() {
        viewModelScope.launch { repository.clearCart() }
    }

    private fun setQuantity(line: CartLine, quantity: Int) {
        viewModelScope.launch { repository.setCartQuantity(line.cartItem.id, quantity) }
    }
}
