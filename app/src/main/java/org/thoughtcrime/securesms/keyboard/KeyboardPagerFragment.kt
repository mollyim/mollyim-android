
package org.thoughtcrime.securesms.keyboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.sticker.StickerKeyboardPageFragment
import org.thoughtcrime.securesms.util.visible
import kotlin.reflect.KClass

class KeyboardPagerFragment : Fragment(R.layout.keyboard_pager_fragment) {

  private lateinit var emojiButton: View
  private lateinit var stickerButton: View
  private lateinit var viewModel: KeyboardPagerViewModel

  private val fragments: MutableMap<KClass<*>, Fragment> = mutableMapOf()
  private var currentFragment: Fragment? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    emojiButton = view.findViewById(R.id.keyboard_pager_fragment_emoji)
    stickerButton = view.findViewById(R.id.keyboard_pager_fragment_sticker)

    viewModel = ViewModelProviders.of(requireActivity())[KeyboardPagerViewModel::class.java]

    viewModel.page().observe(viewLifecycleOwner, this::onPageSelected)
    viewModel.pages().observe(viewLifecycleOwner) { pages ->
      emojiButton.visible = pages.contains(KeyboardPage.EMOJI) && pages.size > 1
      stickerButton.visible = pages.contains(KeyboardPage.STICKER) && pages.size > 1
    }

    emojiButton.setOnClickListener { viewModel.switchToPage(KeyboardPage.EMOJI) }
    stickerButton.setOnClickListener { viewModel.switchToPage(KeyboardPage.STICKER) }
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    viewModel.page().value?.let(this::onPageSelected)
  }

  private fun onPageSelected(page: KeyboardPage) {
    emojiButton.isSelected = page == KeyboardPage.EMOJI
    stickerButton.isSelected = page == KeyboardPage.STICKER

    when (page) {
      KeyboardPage.EMOJI -> displayEmojiPage()
      KeyboardPage.STICKER -> displayStickerPage()
    }

    findListener<MediaKeyboard.MediaKeyboardListener>()?.onKeyboardChanged(page)
  }

  private fun displayEmojiPage() = displayPage(::EmojiKeyboardPageFragment)

  private fun displayStickerPage() = displayPage(::StickerKeyboardPageFragment)

  private inline fun <reified F : Fragment> displayPage(fragmentFactory: () -> F) {
    if (currentFragment is F) {
      return
    }

    val transaction = childFragmentManager.beginTransaction()

    currentFragment?.let { transaction.hide(it) }

    var fragment = fragments[F::class]
    if (fragment == null) {
      fragment = fragmentFactory()
      transaction.add(R.id.fragment_container, fragment)
      fragments[F::class] = fragment
    } else {
      transaction.show(fragment)
    }

    currentFragment = fragment
    transaction.commitAllowingStateLoss()
  }

  fun show() {
    if (isAdded && view != null) {
      viewModel.page().value?.let(this::onPageSelected)
    }
  }

  fun hide() {
    if (isAdded && view != null) {
      val transaction = childFragmentManager.beginTransaction()
      fragments.values.forEach { transaction.remove(it) }
      transaction.commitAllowingStateLoss()
      currentFragment = null
      fragments.clear()
    }
  }
}
