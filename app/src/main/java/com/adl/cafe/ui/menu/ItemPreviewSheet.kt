package com.adl.cafe.ui.menu

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem as VideoClip
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.adl.cafe.R
import com.adl.cafe.data.model.MenuItem
import com.adl.cafe.databinding.SheetItemPreviewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Half-screen sheet shown when a menu item's emoji is tapped: a looping, muted
 * video of the item above its description.
 *
 * Clips are MP4 files in `assets/video/`, looked up by name:
 *  1. `video/<item>.mp4`, e.g. `video/flat_white.mp4` for "Flat White"
 *  2. `video/category/<category>.mp4`, e.g. `video/category/espresso.mp4`
 *  3. otherwise (or if the clip fails to play) the item's emoji, shown large.
 * So a new menu item needs no code change, only a file with the right name.
 */
class ItemPreviewSheet : BottomSheetDialogFragment(R.layout.sheet_item_preview) {

    private var _binding: SheetItemPreviewBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = SheetItemPreviewBinding.bind(view)

        val args = requireArguments()
        val name = args.getString(ARG_NAME).orEmpty()
        binding.textDescription.text = args.getString(ARG_DESCRIPTION)
        binding.textEmoji.text = args.getString(ARG_EMOJI)
        binding.video.contentDescription = name

        val clip = findClip(name, args.getString(ARG_CATEGORY).orEmpty())
        if (clip == null) {
            showEmoji()
        } else {
            player = ExoPlayer.Builder(requireContext()).build().apply {
                setMediaItem(VideoClip.fromUri("asset:///$clip"))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) = showEmoji()
                })
                prepare()
            }
            binding.video.player = player
        }
    }

    override fun onStart() {
        super.onStart()
        player?.play()

        // Fixed at half the screen and fully open, so it never peeks lower first.
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.layoutParams.height = resources.displayMetrics.heightPixels / 2
        sheet.requestLayout()
        (dialog as BottomSheetDialog).behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    private fun findClip(name: String, category: String): String? {
        val assets = requireContext().assets
        return listOf(
            "$DIR/${slug(name)}.mp4",
            "$DIR/category/${slug(category)}.mp4"
        ).firstOrNull { path ->
            assets.list(path.substringBeforeLast('/'))
                ?.contains(path.substringAfterLast('/')) == true
        }
    }

    private fun showEmoji() {
        val binding = _binding ?: return
        binding.video.visibility = View.GONE
        binding.textEmoji.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        binding.video.player = null
        player?.release()
        player = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "itemPreview"
        const val ARG_NAME = "name"
        const val ARG_CATEGORY = "category"
        const val ARG_DESCRIPTION = "description"
        const val ARG_EMOJI = "emoji"

        private const val DIR = "video"

        fun newInstance(item: MenuItem) = ItemPreviewSheet().apply {
            arguments = bundleOf(
                ARG_NAME to item.name,
                ARG_CATEGORY to item.category,
                ARG_DESCRIPTION to item.description,
                ARG_EMOJI to item.emoji
            )
        }

        /** "Pain au Chocolat" -> "pain_au_chocolat". */
        fun slug(text: String): String =
            text.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }
}
