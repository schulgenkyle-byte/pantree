package app.pantrie.feature.walkthrough

/**
 * A logical chunk of the guided tour: either the linear main tour everyone sees on first
 * launch, or one of the optional mini-tours the user picks from the Hub.
 *
 * Each segment owns its own ordered list of [TourStep]s. The [WalkthroughViewModel]
 * tracks "which segment am I in + which step within it" rather than a single global
 * step index — that way a user finishing a mini-tour can pop back to the Hub without
 * the global index pointing somewhere meaningless.
 */
internal sealed class TourSegment {
  abstract val id: String
  abstract val steps: List<TourStep>
}

/**
 * The first-launch linear tour. Six steps, ending in the Hub (which is itself the last
 * step's [TourStepKind.Hub] kind — the Hub IS a step inside main, not a separate
 * concept). When the user opens the app for the first time this is what runs.
 */
internal data class MainTour(
  override val id: String = "main",
  override val steps: List<TourStep>,
) : TourSegment()

/**
 * One of the opt-in mini-tours the user picks from the Hub. After the last step the
 * walkthrough VM marks this mini-tour completed and pops back to the Hub.
 *
 * [title] = Hub button label (e.g. "Cocktails (Mixology)")
 * [description] = subtitle below the button (e.g. "Turn it on, browse the bar")
 */
internal data class MiniTour(
  override val id: String,
  val title: String,
  val description: String,
  override val steps: List<TourStep>,
) : TourSegment()
