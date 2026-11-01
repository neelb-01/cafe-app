package com.adl.cafe.ui.chat

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.adl.cafe.R
import com.adl.cafe.databinding.SheetChatBinding
import com.adl.cafe.ui.cafeViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * The menu assistant, opened from the Menu screen's Ask button. Shown with
 * `show(childFragmentManager, TAG)` like [com.adl.cafe.ui.menu.ItemPreviewSheet],
 * not as a nav destination, so the bottom bar stays put behind it.
 */
class ChatSheet : BottomSheetDialogFragment(R.layout.sheet_chat) {

    // Owned by the Menu screen, so the conversation survives closing the sheet.
    private val viewModel: ChatViewModel by viewModels(
        ownerProducer = { requireParentFragment() },
        factoryProducer = { cafeViewModelFactory() }
    )

    private var _binding: SheetChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private var shownEntries = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = SheetChatBinding.bind(view)

        chatAdapter = ChatAdapter(onAdd = viewModel::add)
        binding.recyclerChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = chatAdapter
        }

        listOf(binding.chipStarterCold, binding.chipStarterBreakfast, binding.chipStarterOrder)
            .forEach { chip -> chip.setOnClickListener { viewModel.send(chip.text.toString()) } }
        binding.buttonSend.setOnClickListener { sendInput() }
        binding.inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendInput()
                true
            } else {
                false
            }
        }
        binding.buttonRetry.setOnClickListener { viewModel.retry() }

        observeState()
    }

    private fun sendInput() {
        val text = binding.inputMessage.text?.toString().orEmpty()
        if (text.isBlank() || viewModel.state.value.isSending) return
        viewModel.send(text)
        binding.inputMessage.text = null
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val rows = state.entries.mapIndexed { index, entry ->
                        ChatRow(
                            index,
                            entry,
                            state.added.filter { it.entry == index }.mapTo(HashSet()) { it.position }
                        )
                    }
                    chatAdapter.submitList(rows) {
                        // Follow new messages, but not taps on an older reply's cards.
                        if (rows.size > shownEntries) {
                            _binding?.recyclerChat?.scrollToPosition(rows.size - 1)
                        }
                        shownEntries = rows.size
                    }
                    binding.intro.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                    binding.rowThinking.visibility = if (state.isSending) View.VISIBLE else View.GONE
                    binding.rowError.visibility = if (state.failed) View.VISIBLE else View.GONE
                    binding.buttonSend.isEnabled = !state.isSending
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Most of the screen and fully open, with room for a conversation.
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.layoutParams.height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_FRACTION).toInt()
        sheet.requestLayout()
        (dialog as BottomSheetDialog).behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        // The adapter captures the ViewModel callbacks; drop it with the view.
        binding.recyclerChat.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "chat"
        private const val SHEET_HEIGHT_FRACTION = 0.85f
    }
}
