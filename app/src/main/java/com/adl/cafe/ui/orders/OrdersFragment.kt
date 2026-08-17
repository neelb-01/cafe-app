package com.adl.cafe.ui.orders

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.adl.cafe.R
import com.adl.cafe.databinding.FragmentOrdersBinding
import com.adl.cafe.ui.cafeViewModelFactory
import kotlinx.coroutines.launch

class OrdersFragment : Fragment(R.layout.fragment_orders) {

    private val viewModel: OrdersViewModel by viewModels { cafeViewModelFactory() }

    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!

    private lateinit var ordersAdapter: OrdersAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOrdersBinding.bind(view)

        ordersAdapter = OrdersAdapter()
        binding.recyclerOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ordersAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.orders.collect { orders ->
                    ordersAdapter.submitList(orders)
                    binding.emptyState.visibility =
                        if (orders.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.recyclerOrders.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
