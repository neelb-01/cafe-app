package com.adl.cafe.ui.menu

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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.adl.cafe.R
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.databinding.FragmentMenuBinding
import com.adl.cafe.ui.cafeViewModelFactory
import com.adl.cafe.ui.recommendations.RecommendationsRowAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MenuFragment : Fragment(R.layout.fragment_menu) {

    private val viewModel: MenuViewModel by viewModels { cafeViewModelFactory() }

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    private lateinit var menuAdapter: MenuAdapter
    private lateinit var recommendationsAdapter: RecommendationsRowAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    /** The filter the list currently shows, to spot when new results replace it. */
    private var shownFilter: MenuFilter? = null
    private var scrollToTopPending = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMenuBinding.bind(view)

        setupLists()
        setupSearch()
        observeState()
    }

    private fun setupLists() {
        menuAdapter = MenuAdapter(
            onClick = ::openDetail,
            onQuickAdd = viewModel::quickAdd,
            onEmojiClick = { item ->
                // A fast double tap would otherwise stack two sheets.
                if (childFragmentManager.findFragmentByTag(ItemPreviewSheet.TAG) == null) {
                    ItemPreviewSheet.newInstance(item).show(childFragmentManager, ItemPreviewSheet.TAG)
                }
            }
        )
        recommendationsAdapter = RecommendationsRowAdapter(
            titleRes = R.string.for_you,
            onClick = ::openDetail,
            onQuickAdd = viewModel::quickAdd
        )
        binding.recyclerMenu.apply {
            layoutManager = LinearLayoutManager(requireContext())
            // A header in the same list, so the row scrolls away with the menu.
            adapter = ConcatAdapter(recommendationsAdapter, menuAdapter)
            setHasFixedSize(true)
        }

        categoryAdapter = CategoryAdapter(onSelect = viewModel::selectCategory)
        binding.recyclerCategories.apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = categoryAdapter
        }
    }

    private fun openDetail(item: MenuItem) {
        findNavController().navigate(
            R.id.action_menu_to_detail,
            bundleOf(ARG_ITEM_ID to item.id)
        )
    }

    private fun setupSearch() {
        binding.inputSearch.doAfterTextChanged { text ->
            viewModel.search(text?.toString().orEmpty())
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        categoryAdapter.submitList(
                            buildList {
                                add(
                                    Category(
                                        name = null,
                                        label = getString(R.string.category_all),
                                        selected = state.selectedCategory == null
                                    )
                                )
                                state.categories.forEach { category ->
                                    add(
                                        Category(
                                            name = category,
                                            label = category,
                                            selected = state.selectedCategory == category
                                        )
                                    )
                                }
                            }
                        )
                        if (shownFilter != null && shownFilter != state.itemsFilter) {
                            scrollToTopPending = true
                        }
                        shownFilter = state.itemsFilter
                        recommendationsAdapter.submitList(state.recommendations)
                        menuAdapter.submitList(state.items) {
                            // RecyclerView holds the top visible item in place, so
                            // results that land above it (and the For you row) would
                            // be off-screen. A new filter starts from the top.
                            if (scrollToTopPending) {
                                scrollToTopPending = false
                                _binding?.recyclerMenu?.scrollToPosition(0)
                            }
                        }
                        binding.emptyState.visibility =
                            if (state.isEmpty) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.addedToCart.collect { name ->
                        if (name != null) {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.added_to_cart, name),
                                Snackbar.LENGTH_SHORT
                            ).show()
                            viewModel.consumeAddedToCart()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        // Adapters capture the binding, so drop them before releasing it.
        binding.recyclerMenu.adapter = null
        binding.recyclerCategories.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_ITEM_ID = "itemId"
    }
}
