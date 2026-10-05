package com.adl.cafe.ui.cart

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.adl.cafe.R
import com.adl.cafe.data.Pricing
import com.adl.cafe.databinding.FragmentCartBinding
import com.adl.cafe.ui.cafeViewModelFactory
import com.adl.cafe.ui.menu.MenuFragment
import com.adl.cafe.ui.recommendations.RecommendationsRowAdapter
import com.adl.cafe.util.asMoney
import kotlinx.coroutines.launch

class CartFragment : Fragment(R.layout.fragment_cart) {

    private val viewModel: CartViewModel by viewModels { cafeViewModelFactory() }

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private lateinit var cartAdapter: CartAdapter
    private lateinit var pairingsAdapter: RecommendationsRowAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCartBinding.bind(view)

        binding.textTaxLabel.text = getString(R.string.tax, Pricing.taxRateLabel)

        cartAdapter = CartAdapter(
            onIncrement = viewModel::increment,
            onDecrement = viewModel::decrement,
            onRemove = viewModel::remove
        )
        pairingsAdapter = RecommendationsRowAdapter(
            titleRes = R.string.goes_well_with,
            onClick = { item ->
                findNavController().navigate(
                    R.id.action_cart_to_detail,
                    bundleOf(MenuFragment.ARG_ITEM_ID to item.id)
                )
            },
            // The item lands in the cart above, which is feedback enough.
            onQuickAdd = viewModel::quickAdd
        )
        binding.recyclerCart.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ConcatAdapter(cartAdapter, pairingsAdapter)
        }

        binding.buttonCheckout.setOnClickListener {
            findNavController().navigate(R.id.action_cart_to_checkout)
        }
        binding.buttonClear.setOnClickListener { viewModel.clear() }
        binding.buttonBrowseMenu.setOnClickListener {
            findNavController().navigate(R.id.menuFragment)
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    cartAdapter.submitList(state.lines)
                    pairingsAdapter.submitList(state.pairings)

                    binding.emptyState.visibility =
                        if (state.isEmpty) View.VISIBLE else View.GONE
                    binding.contentGroup.visibility =
                        if (state.isEmpty) View.GONE else View.VISIBLE

                    binding.textSubtotal.text = state.subtotalCents.asMoney()
                    binding.textTax.text = state.taxCents.asMoney()
                    binding.textTotal.text = state.totalCents.asMoney()
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.recyclerCart.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
