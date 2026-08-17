package com.adl.cafe.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adl.cafe.data.CafeRepository
import com.adl.cafe.data.model.DrinkSize
import com.adl.cafe.data.model.MenuItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val item: MenuItem? = null,
    val size: DrinkSize = DrinkSize.SMALL,
    val quantity: Int = 1
) {
    val unitPriceCents: Int
        get() = (item?.priceCents ?: 0) + if (item?.sizable == true) size.surchargeCents else 0

    val totalCents: Int get() = unitPriceCents * quantity
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(private val repository: CafeRepository) : ViewModel() {

    private val itemId = MutableStateFlow<Long?>(null)
    private val size = MutableStateFlow(DrinkSize.SMALL)
    private val quantity = MutableStateFlow(1)

    private val item = itemId.filterNotNull().flatMapLatest { repository.observeMenuItem(it) }

    val state: StateFlow<DetailUiState> = combine(item, size, quantity) { item, size, quantity ->
        DetailUiState(item = item, size = size, quantity = quantity)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState()
    )

    private val _addedToCart = MutableStateFlow(false)
    val addedToCart: StateFlow<Boolean> = _addedToCart.asStateFlow()

    /** Called once from the fragment with the id passed through the nav argument. */
    fun load(id: Long) {
        if (itemId.value == null) itemId.value = id
    }

    fun selectSize(newSize: DrinkSize) {
        size.value = newSize
    }

    fun increment() {
        quantity.value = (quantity.value + 1).coerceAtMost(MAX_QUANTITY)
    }

    fun decrement() {
        quantity.value = (quantity.value - 1).coerceAtLeast(1)
    }

    fun addToCart() {
        val current = state.value
        val item = current.item ?: return
        viewModelScope.launch {
            repository.addToCart(
                menuItemId = item.id,
                size = if (item.sizable) current.size else DrinkSize.SMALL,
                quantity = current.quantity
            )
            _addedToCart.value = true
        }
    }

    fun consumeAddedToCart() {
        _addedToCart.value = false
    }

    private companion object {
        const val MAX_QUANTITY = 20
    }
}
