package com.adl.cafe.ui.detail

import android.os.Bundle
import android.view.View
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.adl.cafe.R
import com.adl.cafe.data.model.DrinkSize
import com.adl.cafe.databinding.FragmentDetailBinding
import com.adl.cafe.ui.cafeViewModelFactory
import com.adl.cafe.ui.menu.MenuFragment
import com.adl.cafe.util.asMoney
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class DetailFragment : Fragment(R.layout.fragment_detail) {

    private val viewModel: DetailViewModel by viewModels { cafeViewModelFactory() }

    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailBinding.bind(view)

        viewModel.load(requireArguments().getLong(MenuFragment.ARG_ITEM_ID))

        buildSizeChips()
        binding.buttonIncrement.setOnClickListener { viewModel.increment() }
        binding.buttonDecrement.setOnClickListener { viewModel.decrement() }
        binding.buttonAddToCart.setOnClickListener { viewModel.addToCart() }

        observeState()
    }

    private fun buildSizeChips() {
        DrinkSize.entries.forEach { size ->
            val chip = layoutInflater.inflate(
                R.layout.item_size_chip,
                binding.chipGroupSize,
                false
            ) as Chip
            chip.id = View.generateViewId()
            chip.text = if (size.surchargeCents == 0) {
                size.label
            } else {
                getString(R.string.size_with_surcharge, size.label, size.surchargeCents.asMoney())
            }
            chip.tag = size
            chip.setOnClickListener {
                chip.isChecked = true
                viewModel.selectSize(size)
            }
            binding.chipGroupSize.addView(chip)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        val item = state.item ?: return@collect

                        binding.textEmoji.text = item.emoji
                        binding.textName.text = item.name
                        binding.textDescription.text = item.description
                        binding.textCategory.text = item.category
                        binding.textQuantity.text = state.quantity.toString()
                        binding.buttonAddToCart.text =
                            getString(R.string.add_to_cart_with_total, state.totalCents.asMoney())

                        // Food has no size options, so hide the whole section.
                        val sizeVisibility = if (item.sizable) View.VISIBLE else View.GONE
                        binding.labelSize.visibility = sizeVisibility
                        binding.chipGroupSize.visibility = sizeVisibility

                        syncSizeSelection(state.size)
                    }
                }
                launch {
                    viewModel.addedToCart.collect { added ->
                        if (added) {
                            viewModel.consumeAddedToCart()
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }
    }

    private fun syncSizeSelection(selected: DrinkSize) {
        binding.chipGroupSize.children.filterIsInstance<Chip>().forEach { chip ->
            chip.isChecked = chip.tag == selected
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
