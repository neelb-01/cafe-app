package com.adl.cafe.ui

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adl.cafe.CafeApplication
import com.adl.cafe.data.CafeRepository
import com.adl.cafe.ui.cart.CartViewModel
import com.adl.cafe.ui.checkout.CheckoutViewModel
import com.adl.cafe.ui.detail.DetailViewModel
import com.adl.cafe.ui.menu.MenuViewModel
import com.adl.cafe.ui.orders.OrdersViewModel

/** Hands every ViewModel in the app the shared repository. */
class CafeViewModelFactory(
    private val repository: CafeRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(MenuViewModel::class.java) -> MenuViewModel(repository)
        modelClass.isAssignableFrom(DetailViewModel::class.java) -> DetailViewModel(repository)
        modelClass.isAssignableFrom(CartViewModel::class.java) -> CartViewModel(repository)
        modelClass.isAssignableFrom(CheckoutViewModel::class.java) -> CheckoutViewModel(repository)
        modelClass.isAssignableFrom(OrdersViewModel::class.java) -> OrdersViewModel(repository)
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}

/** `by viewModels { cafeViewModelFactory() }` in any fragment. */
fun Fragment.cafeViewModelFactory(): CafeViewModelFactory =
    CafeViewModelFactory((requireActivity().application as CafeApplication).repository)
