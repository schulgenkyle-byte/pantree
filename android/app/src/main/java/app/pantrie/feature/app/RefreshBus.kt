package app.pantrie.feature.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide refresh signals. Used when one screen's action mutates state another
 * screen renders (e.g. Tonight swipe-save adds to shopping → Shopping screen needs
 * to know). Lightweight SharedFlow — receivers just re-fetch on emission.
 */
@Singleton
class RefreshBus @Inject constructor() {
  private val _shopping = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 4)
  val shopping = _shopping.asSharedFlow()

  private val _pantry = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 4)
  val pantry = _pantry.asSharedFlow()

  // Emitted when a recipe is saved (swipe-right or save-tap on Tonight). Drives the
  // first-launch tour's "tap save" completion trigger so the overlay auto-advances
  // off the swipe step the moment the user actually does it.
  private val _recipeSaved = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 4)
  val recipeSaved = _recipeSaved.asSharedFlow()

  fun bumpShopping() { _shopping.tryEmit(System.currentTimeMillis()) }
  fun bumpPantry() { _pantry.tryEmit(System.currentTimeMillis()) }
  fun bumpRecipeSaved() { _recipeSaved.tryEmit(System.currentTimeMillis()) }
}
