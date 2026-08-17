package com.adl.cafe.ui.confirmation

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.adl.cafe.R
import com.adl.cafe.data.model.OrderType
import com.adl.cafe.databinding.FragmentOrderConfirmedBinding
import com.adl.cafe.ui.cafeViewModelFactory
import com.adl.cafe.ui.orders.OrdersViewModel
import com.adl.cafe.util.asMoney
import kotlinx.coroutines.launch

class OrderConfirmedFragment : Fragment(R.layout.fragment_order_confirmed) {

    private val viewModel: OrdersViewModel by viewModels { cafeViewModelFactory() }

    private var _binding: FragmentOrderConfirmedBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOrderConfirmedBinding.bind(view)

        val orderId = requireArguments().getLong(ARG_ORDER_ID)

        binding.buttonBackToMenu.setOnClickListener {
            // Clear the checkout/cart stack so Back doesn't re-enter the flow.
            findNavController().navigate(
                R.id.menuFragment,
                null,
                navOptions {
                    popUpTo(R.id.menuFragment) { inclusive = true }
                }
            )
        }
        binding.buttonViewOrders.setOnClickListener {
            findNavController().navigate(
                R.id.ordersFragment,
                null,
                navOptions {
                    popUpTo(R.id.menuFragment) { inclusive = false }
                }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.observeOrder(orderId).collect { order ->
                    if (order == null) return@collect
                    binding.textOrderNumber.text = order.order.orderNumber
                    binding.textTotal.text = order.order.totalCents.asMoney()
                    binding.textSummary.text = resources.getQuantityString(
                        R.plurals.item_count,
                        order.itemCount,
                        order.itemCount
                    )
                    binding.textFulfilment.text = when (order.order.orderType) {
                        OrderType.PICKUP -> getString(R.string.confirmation_pickup)
                        OrderType.DINE_IN -> getString(R.string.confirmation_dine_in)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_ORDER_ID = "orderId"
    }
}
