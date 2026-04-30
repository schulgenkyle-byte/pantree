package app.pantrie.feature.walkthrough

import app.pantrie.Brand

internal enum class TourStepKind { FullSheet, Spotlight, Hub }

/**
 * Auto-advance trigger for a guided tour step. The walkthrough overlay watches the
 * current step's [TourTrigger] against live app state (current route, pantry-item count,
 * recipe-save count) and advances the step the moment the trigger condition fires.
 *
 * The user does NOT see a "Next" button on Spotlight steps; they tap the highlighted
 * UI element (which is click-transparent in the cutout — the tap reaches the underlying
 * button) and the resulting state change auto-advances the tour. [TooltipTap] is the
 * fallback for steps that have no good observable state (or for users who can't figure
 * out what to tap and need an escape hatch).
 *
 * [None] is for FullSheet / Hub steps which advance via their own button taps.
 */
internal sealed interface TourTrigger {
  data object None : TourTrigger

  /** Advance when the current nav route equals [route]. */
  data class RouteIs(val route: String) : TourTrigger

  /**
   * Advance when the current nav route is in [routes]. Used for "tap any pantry tab"
   * style steps where multiple destinations satisfy the goal.
   */
  data class RouteIn(val routes: Set<String>) : TourTrigger

  /** Advance when the live pantry-item count is at least [n]. */
  data class PantryCountAtLeast(val n: Int) : TourTrigger

  /** Advance when the user has saved at least [n] recipes since the tour started. */
  data class SaveCountAtLeast(val n: Int) : TourTrigger

  /** Tooltip-tap fallback: the user can tap the tooltip card to advance manually. */
  data object TooltipTap : TourTrigger
}

/**
 * One step of the guided walkthrough.
 *
 * [anchorKey] references a key in [TourAnchorRegistry]. The overlay looks up the live
 * measured bounds for that key at draw time and draws the spotlight at the exact center
 * of the target composable. If the key has no reported bounds (composable not currently
 * in composition) the overlay falls back to a full-sheet card so the user sees the copy
 * instead of a misaligned spotlight pointing at empty screen.
 *
 * [anchorKey] is ignored for [TourStepKind.FullSheet] / [TourStepKind.Hub] steps.
 *
 * [trigger] auto-advances the step when its condition becomes true. For FullSheet / Hub
 * steps [trigger] is [TourTrigger.None] and the step's primary button drives the advance.
 *
 * [requiredRoute] (Spotlight only) — the route the user MUST be on for this step's
 * spotlight to be meaningful. The VM auto-navigates here when entering the step. Without
 * this, a mini-tour step that spotlights "settings mixology toggle" would draw on top of
 * whatever screen the user happens to be on.
 *
 * [allowTooltipTap] adds a "or tap here to continue" affordance to the tooltip card so a
 * stuck user can manually advance even if the primary trigger never fires.
 */
internal data class TourStep(
  val kind: TourStepKind,
  val title: String,
  val body: String,
  val anchorKey: String? = null,
  val trigger: TourTrigger = TourTrigger.None,
  val allowTooltipTap: Boolean = true,
  val requiredRoute: String? = null,
)

/**
 * The full tour hierarchy: one main linear tour + five optional mini-tours fanning out
 * from the Hub step at the end of main.
 *
 * Main tour mirrors the original first-launch flow (welcome → pantry tile → snap →
 * culinary → save), but its terminal step is now a Hub instead of a flat "you're set"
 * card. The Hub renders a button per mini-tour; the user picks one or hits "Self
 * explore" to dismiss the tour entirely.
 *
 * Copy follows the user's writing-voice rules: lowercase conversational intros where
 * natural, plain prose, no em-dashes, no bold marketing headers in body text.
 */
internal object TourSteps {

  // ---------- Main tour ----------
  // 6-moment intuitive flow: photo → match → swipe → cook → library → signature.
  // Each step is a single action with an immediate aha. No "this is the X tab"
  // narration. Copy is short by design — the whole tour reads in under sixty
  // seconds. Each step's trigger watches live state so the user advances by
  // doing the thing, not by tapping Next.
  val mainTour: MainTour = MainTour(
    steps = listOf(

      // 0. THE OPEN. One promise, one number, one button.
      TourStep(
        kind = TourStepKind.FullSheet,
        title = "Sixty seconds.",
        body = "Photo. Match. Cook. Save. Then you've got it.",
        trigger = TourTrigger.None,
      ),

      // 1. THE FRIDGE MOMENT (1/2). Open pantry. No "this is where things live"
      // narration — the user finds out by going there.
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Open your pantry.",
        body = "We'll show you what we see.",
        anchorKey = TourAnchors.DECK_PANTRY_TILE,
        trigger = TourTrigger.RouteIs("pantry"),
        requiredRoute = "deck",
      ),

      // 2. THE FRIDGE MOMENT (2/2). The aha: photograph → ingredients appear.
      // Trigger is pantry count crossing zero, so the user only sees the next
      // step after the magic actually happened.
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Snap your fridge.",
        body = "Watch what we pull out of the photo.",
        anchorKey = TourAnchors.PANTRY_SCAN_BUTTON,
        trigger = TourTrigger.PantryCountAtLeast(1),
        requiredRoute = "pantry",
      ),

      // 3. THE MATCH MOMENT. Back to deck. The card now reflects the user's
      // actual pantry. No badge legend pre-amble — the user sees the % and
      // gets it.
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Tonight's pick.",
        body = "Ranked by what you already have. Tap Culinary.",
        anchorKey = TourAnchors.NAV_CULINARY,
        trigger = TourTrigger.RouteIs("deck"),
      ),

      // 3.5. ONE LINE on what the % means. Used to be a paragraph; now it
      // reads in three seconds.
      TourStep(
        kind = TourStepKind.FullSheet,
        title = "The percent on each card.",
        body = "How much of the recipe you already own. Higher means cook it tonight.",
        trigger = TourTrigger.None,
      ),

      // 4. THE SWIPE MOMENT. Visceral instruction. The drag IS the explanation.
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Drag it right to save.",
        body = "Missing ingredients land in your shopping list automatically.",
        anchorKey = TourAnchors.DECK_CARD_AREA,
        trigger = TourTrigger.SaveCountAtLeast(1),
        requiredRoute = "deck",
      ),

      // 5. THE LOOP CARD. Names the four-beat loop the user just lived through.
      // Sets the user up to find Cook mode + Library on their own.
      TourStep(
        kind = TourStepKind.FullSheet,
        title = "That's the loop.",
        body = "Photo, Match, Cook, Save. The rest is yours to find.",
        trigger = TourTrigger.None,
      ),

      // 6. HUB. Branching point. Renders mini-tour buttons (Library, Mixology,
      // Submit, Shopping, Plan, Pantry-photo) + Self explore.
      TourStep(
        kind = TourStepKind.Hub,
        title = "Want to keep going?",
        body = "Pick what to learn next, or skip and explore.",
        trigger = TourTrigger.None,
      ),
    ),
  )

  // ---------- Mini-tour ids (string constants so call sites and persistence agree) ----------
  const val MINI_MIXOLOGY = "mini_mixology"
  const val MINI_ADD_RECIPE = "mini_add_recipe"
  const val MINI_SHOPPING = "mini_shopping"
  const val MINI_PANTRY_PHOTO = "mini_pantry_photo"
  const val MINI_PLAN = "mini_plan"
  const val MINI_LIBRARY = "mini_library"

  // ---------- Mini-tour: Cocktails / Mixology ----------
  // Walks the user through enabling the Mixology tab in Settings, opening it, and
  // toggling Bootlegger ↔ Mixologist sub-modes. Settings toggle uses TooltipTap because
  // there's no observable signal we want to wait on (the tab appearance is the signal,
  // and step 3 catches that via RouteIs("mixology")).
  private val miniMixology = MiniTour(
    id = MINI_MIXOLOGY,
    title = "Cocktails (Mixology)",
    description = "Turn it on, browse the bar.",
    steps = listOf(
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Open Settings",
        body = "First we need to turn Mixology on. Tap the You tab.",
        anchorKey = TourAnchors.NAV_YOU,
        trigger = TourTrigger.RouteIs("settings"),
      ),
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Flip the Mixology switch",
        body = "Toggle this on. The cocktail tab shows up in your bottom nav.",
        anchorKey = TourAnchors.SETTINGS_MIXOLOGY_TOGGLE,
        trigger = TourTrigger.TooltipTap,
        requiredRoute = "settings",
      ),
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Tap the Mixology tab",
        body = "You'll see cocktails ranked by what's already in your bar.",
        anchorKey = TourAnchors.NAV_MIXOLOGY,
        trigger = TourTrigger.RouteIs("mixology"),
      ),
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Pick your era",
        body = "Bootlegger is pre-prohibition classics, verbatim. Mixologist is modern craft. Try one.",
        anchorKey = TourAnchors.MIXOLOGY_MODE_TOGGLE,
        trigger = TourTrigger.TooltipTap,
        requiredRoute = "mixology",
      ),
      TourStep(
        kind = TourStepKind.FullSheet,
        title = "You're set on cocktails.",
        body = "Swipe through the deck the same way you did for food. Saved drinks live alongside saved recipes.",
        trigger = TourTrigger.None,
      ),
    ),
  )

  // ---------- Mini-tour: Add your own recipe ----------
  // Educational only — does NOT require a Pro purchase to complete. Photo-to-recipe is
  // a Pro feature; the mini-tour points the user at where it lives and explains the gate.
  private val miniAddRecipe = MiniTour(
    id = MINI_ADD_RECIPE,
    title = "Add your own recipe",
    description = "Snap a recipe, we extract it.",
    steps = listOf(
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Open Settings",
        body = "Submitting a recipe lives in your You tab.",
        anchorKey = TourAnchors.NAV_YOU,
        trigger = TourTrigger.RouteIs("settings"),
      ),
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Tap My submissions",
        body = "From here you can start a new submission. Snap a photo of any recipe and we extract the ingredients and steps.",
        anchorKey = TourAnchors.SETTINGS_SUBMISSIONS_BUTTON,
        trigger = TourTrigger.RouteIs("my_submissions"),
        requiredRoute = "settings",
      ),
      TourStep(
        kind = TourStepKind.FullSheet,
        title = "A ${Brand.PRO_NAME} feature",
        body = "Photo to recipe extraction is part of ${Brand.PRO_NAME}. Free accounts can still type a submission in by hand.",
        trigger = TourTrigger.None,
      ),
    ),
  )

  // ---------- Mini-tour: Smart shopping list ----------
  private val miniShopping = MiniTour(
    id = MINI_SHOPPING,
    title = "Smart shopping list",
    description = "Save a recipe, the missing stuff lands here.",
    steps = listOf(
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Open Shopping",
        body = "Whatever's missing for the recipes you've saved lands here, grouped by aisle.",
        anchorKey = TourAnchors.DECK_SHOPPING_TILE,
        trigger = TourTrigger.RouteIs("shopping"),
        requiredRoute = "deck",
      ),
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Share the list",
        body = "Tap share to text the list to whoever's running the errand. Only unchecked items go.",
        anchorKey = TourAnchors.SHOPPING_SHARE_BUTTON,
        trigger = TourTrigger.TooltipTap,
        requiredRoute = "shopping",
      ),
      TourStep(
        kind = TourStepKind.FullSheet,
        title = "Grows on its own",
        body = "Every recipe you save adds the missing ingredients here. Long-press a row to move it to a different aisle.",
        trigger = TourTrigger.None,
      ),
    ),
  )

  // ---------- Mini-tour: Snap your fridge ----------
  // Overlaps with main steps 1-2. The VM auto-marks this completed if pantry already has
  // items at hub-arrival time so the Hub shows a checkmark instead of nudging the user
  // to redo something they did five seconds ago.
  private val miniPantryPhoto = MiniTour(
    id = MINI_PANTRY_PHOTO,
    title = "Snap your fridge",
    description = "Add what you have without typing.",
    steps = listOf(
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Open your pantry",
        body = "From the deck, tap pantry.",
        anchorKey = TourAnchors.DECK_PANTRY_TILE,
        trigger = TourTrigger.RouteIs("pantry"),
        requiredRoute = "deck",
      ),
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Snap your shelves",
        body = "Tap the camera. A few photos of your fridge or pantry shelves and we'll add what we see.",
        anchorKey = TourAnchors.PANTRY_SCAN_BUTTON,
        trigger = TourTrigger.TooltipTap,
        requiredRoute = "pantry",
      ),
      TourStep(
        kind = TourStepKind.FullSheet,
        title = "Now your pantry knows what you have.",
        body = "Recipes will rank higher when you already own the ingredients.",
        trigger = TourTrigger.None,
      ),
    ),
  )

  // ---------- Mini-tour: Plan your week ----------
  private val miniPlan = MiniTour(
    id = MINI_PLAN,
    title = "Plan your week",
    description = "Seven dinners from your pantry.",
    steps = listOf(
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Open Plan",
        body = "We'll lay out a week's worth of dinners using what you have.",
        anchorKey = TourAnchors.DECK_PLAN_TILE,
        trigger = TourTrigger.RouteIs("plan"),
        requiredRoute = "deck",
      ),
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Plan it for me",
        body = "Tap and we pick seven dinners that use what's in your pantry, prioritizing what's expiring.",
        anchorKey = TourAnchors.PLAN_GENERATE_BUTTON,
        trigger = TourTrigger.TooltipTap,
        requiredRoute = "plan",
      ),
      TourStep(
        kind = TourStepKind.FullSheet,
        title = "Lock in the week",
        body = "When you're happy with the picks, tap Lock in. Anything missing gets auto-added to your shopping list.",
        trigger = TourTrigger.None,
      ),
    ),
  )

  // ---------- Mini-tour: Build your own cookbooks ----------
  // Library is the most-undersold feature on the marketing site; this tour
  // teaches the three-level shape (Library → Books → Chapters → Recipes) and
  // the share-by-link mechanic that turns a private cookbook into a shareable
  // artifact. Anchored on the "Open Library" affordance on SavedScreen.
  private val miniLibrary = MiniTour(
    id = MINI_LIBRARY,
    title = "Your own cookbooks",
    description = "Save into Books. Share by link.",
    steps = listOf(
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Tap the Feed tab.",
        body = "Library lives next to your saved recipes.",
        anchorKey = TourAnchors.NAV_FEED,
        trigger = TourTrigger.RouteIs("community"),
      ),
      TourStep(
        kind = TourStepKind.Spotlight,
        title = "Open Library.",
        body = "Two starter Books wait for you. Saved, and My Recipes.",
        anchorKey = TourAnchors.SAVED_LIBRARY_BUTTON,
        trigger = TourTrigger.RouteIs("library"),
        requiredRoute = "community",
      ),
      TourStep(
        kind = TourStepKind.FullSheet,
        title = "Books and chapters.",
        body = "Build as many Books as you want. Chapters inside. Drop any recipe in. Send a Book to a friend by link, no app needed on their end.",
        trigger = TourTrigger.None,
      ),
    ),
  )

  /** All mini-tours, in the order the Hub renders their buttons. */
  val miniTours: List<MiniTour> = listOf(
    miniLibrary,
    miniMixology,
    miniAddRecipe,
    miniShopping,
    miniPantryPhoto,
    miniPlan,
  )

  /** Lookup helper for the VM. */
  fun miniTourById(id: String): MiniTour? = miniTours.firstOrNull { it.id == id }
}
