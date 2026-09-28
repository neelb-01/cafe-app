package com.adl.cafe.ui.checkout

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.adl.cafe.R
import com.adl.cafe.data.Pricing
import com.adl.cafe.data.model.OrderType
import com.adl.cafe.databinding.FragmentCheckoutBinding
import com.adl.cafe.ui.cafeViewModelFactory
import com.adl.cafe.ui.confirmation.OrderConfirmedFragment
import com.adl.cafe.util.asMoney
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CheckoutFragment : Fragment(R.layout.fragment_checkout) {

    private val viewModel: CheckoutViewModel by viewModels { cafeViewModelFactory() }

    private var _binding: FragmentCheckoutBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCheckoutBinding.bind(view)

        binding.textTaxLabel.text = getString(R.string.tax, Pricing.taxRateLabel)

        binding.inputName.doAfterTextChanged { viewModel.clearNameError() }

        binding.buttonPlaceOrder.setOnClickListener {
            viewModel.placeOrder(
                name = binding.inputName.text?.toString().orEmpty(),
                orderType = selectedOrderType(),
                note = binding.inputNote.text?.toString().orEmpty()
            )
        }

        observeState()
    }

    private fun selectedOrderType(): OrderType =
        if (binding.buttonDineIn.isChecked) OrderType.DINE_IN else OrderType.PICKUP

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.totals.collect { totals ->
                        binding.textItemCount.text = resources.getQuantityString(
                            R.plurals.item_count,
                            totals.itemCount,
                            totals.itemCount
                        )
                        binding.textSubtotal.text = totals.subtotalCents.asMoney()
                        binding.textTax.text = totals.taxCents.asMoney()
                        binding.textTotal.text = totals.totalCents.asMoney()
                    }
                }
                launch {
                    viewModel.canPlaceOrder.collect { enabled ->
                        binding.buttonPlaceOrder.isEnabled = enabled
                    }
                }
                launch {
                    viewModel.nameError.collect { hasError ->
                        binding.layoutName.error =
                            if (hasError) getString(R.string.error_name_required) else null
                    }
                }
                launch {
                    viewModel.placedOrderId.collect { orderId ->
                        if (orderId != null) {
                            viewModel.consumePlacedOrder()
                            findNavController().navigate(
                                R.id.action_checkout_to_confirmation,
                                bundleOf(OrderConfirmedFragment.ARG_ORDER_ID to orderId)
                            )
                        }
                    }
                }
                launch {
                    viewModel.orderFailed.collect { failed ->
                        if (failed) {
                            Snackbar.make(
                                binding.root,
                                R.string.error_order_failed,
                                Snackbar.LENGTH_LONG
                            ).show()
                            viewModel.consumeOrderFailed()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
