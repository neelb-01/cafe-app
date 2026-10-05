package com.adl.cafe.ui.menu

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A category chip (null is "All") plus search text. */
data class MenuFilter(val category: String?, val query: String) {
    val isActive: Boolean get() = category != null || query.isNotBlank()
}

data class MenuUiState(
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val items: List<MenuItem> = emptyList(),
    /**
     * The filter [items] were fetched for. It lags [query] and [selectedCategory]
     * until the new results arrive, so it changes in the same state as they do.
     */
    val itemsFilter: MenuFilter = MenuFilter(null, ""),
    /** The "For you" row; empty while a search or category filter is active. */
    val recommendations: List<MenuItem> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModel(private val repository: CafeRepository) : ViewModel() {

    private val selectedCategory = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    /** Re-runs the menu query whenever the category chip or search text changes. */
    private val filteredItems = combine(selectedCategory, query) { category, text ->
        MenuFilter(category, text)
    }.flatMapLatest { filter ->
        repository.observeMenu(filter.category, filter.query).map { items -> filter to items }
    }

    val state: StateFlow<MenuUiState> = combine(
        repository.observeCategories(),
        selectedCategory,
        query,
        filteredItems,
        repository.observeRecommendations()
    ) { categories, category, text, (itemsFilter, items), recommendations ->
        MenuUiState(
            categories = categories,
            selectedCategory = category,
            items = items,
            itemsFilter = itemsFilter,
            // Recommendations sit above the full menu; under a filter they would
            // suggest things the filter has just hidden. Keyed on itemsFilter so
            // the row and the results change in the same frame.
            recommendations = if (itemsFilter.isActive) emptyList() else recommendations,
            query = text,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MenuUiState()
    )

    private val _addedToCart = MutableStateFlow<String?>(null)
    val addedToCart: StateFlow<String?> = _addedToCart.asStateFlow()

    fun selectCategory(category: String?) {
        selectedCategory.value = category
    }

    fun search(text: String) {
        query.value = text
    }

    /** Quick-add from the list uses the default size and a quantity of one. */
    fun quickAdd(item: MenuItem) {
        viewModelScope.launch {
            repository.addToCart(item.id, DrinkSize.SMALL, 1)
            _addedToCart.value = item.name
        }
    }

    fun consumeAddedToCart() {
        _addedToCart.value = null
    }
}
