package app.pantrie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.navigation.compose.*
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.feature.app.AppStateViewModel
import app.pantrie.feature.auth.LoginScreen
import app.pantrie.feature.barcode.BarcodeScreen
import app.pantrie.feature.beta.Analytics
import app.pantrie.feature.beta.BetaFeedbackSheet
import app.pantrie.feature.beta.CommunityScreen
import app.pantrie.feature.cook.CookModeScreen
import app.pantrie.feature.deck.DeckScreen
import app.pantrie.feature.library.BookDetailScreen
import app.pantrie.feature.library.LibraryScreen
import app.pantrie.feature.mealprep.MealPrepScreen
import app.pantrie.feature.mixology.MixologyScreen
import app.pantrie.feature.notifications.NotificationScheduler
import app.pantrie.feature.notifications.RescanWorker
import app.pantrie.feature.onboarding.AgeGateScreen
import app.pantrie.feature.onboarding.OnboardingScreen
import app.pantrie.feature.pantry.PantryScreen
import app.pantrie.feature.plan.PlanScreen
import app.pantrie.feature.recipe.RecipeDetailScreen
import app.pantrie.feature.saved.SavedScreen
import app.pantrie.feature.search.SearchSheet
import app.pantrie.feature.submit.MySubmissionsScreen
import app.pantrie.feature.submit.PhotoToRecipeScreen
import app.pantrie.feature.submit.SubmitRecipeScreen
import app.pantrie.feature.scan.ScanMode
import app.pantrie.feature.scan.ScanScreen
import app.pantrie.feature.settings.LocalSettingsStore
import app.pantrie.feature.settings.SettingsScreen
import app.pantrie.feature.shopping.ShoppingScreen
import app.pantrie.feature.walkthrough.TourAnchors
import app.pantrie.feature.walkthrough.WalkthroughOverlay
import app.pantrie.feature.walkthrough.WalkthroughViewModel
import app.pantrie.network.PantrieApi
import app.pantrie.ui.theme.PantrieTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var analytics: Analytics
  @Inject lateinit var api: PantrieApi
  @Inject lateinit var localSettings: LocalSettingsStore
  @Inject lateinit var localeManager: app.pantrie.locale.LocaleManager

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    analytics.track("app_opened")
    NotificationScheduler.schedule(this)
    // Persist last-app-open for the SwipeRefillWorker's inactivity gate. If the
    // user opens the app within 12h of the cap-refill fire time, the worker
    // skips the notification (don't ping people who are already using the app).
    applicationContext.getSharedPreferences(NotificationScheduler.PREFS, Context.MODE_PRIVATE)
      .edit()
      .putLong(app.pantrie.feature.notifications.SwipeRefillWorker.KEY_LAST_APP_OPEN_MS, System.currentTimeMillis())
      .apply()

    // EXTRA_NAV_TARGET is delivered via PendingIntent from internal WorkManager jobs, but
    // the MainActivity is exported (LAUNCHER), so any installed app can fire an Intent
    // with arbitrary extras. Whitelist the route so a malicious app can't force-open
    // sensitive screens (paywall, scan, settings) for UI-redress / phishing overlay.
    val rawNavTarget = intent?.getStringExtra(RescanWorker.EXTRA_NAV_TARGET)
    val rescanDeepLink = rawNavTarget?.takeIf { it in ALLOWED_DEEP_LINK_ROUTES }

    // ACTION_SEND share-sheet target. TikTok / YouTube / browser "Share to
    // Speakeater" lands here. Pull the first http(s) URL out of EXTRA_TEXT,
    // validate it points at a supported host, and route into the link-import
    // flow with the URL pre-filled. Pro gating happens server-side.
    val sharedImportUrl: String? = if (intent?.action == Intent.ACTION_SEND && intent?.type == "text/plain") {
      val raw = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
      val match = Regex("https?://\\S+").find(raw)?.value
      match?.takeIf { url ->
        runCatching {
          val host = java.net.URI(url).host?.lowercase().orEmpty()
          host.endsWith("tiktok.com") || host.endsWith("youtube.com") || host == "youtu.be"
        }.getOrDefault(false)
      }
    } else null

    val initialNavTarget = sharedImportUrl?.let {
      "import_links?prefill=${java.net.URLEncoder.encode(it, "UTF-8")}"
    } ?: rescanDeepLink

    setContent {
      PantrieTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          // Strict gate order — only one screen on screen at a time:
          //   1. Age gate  (Google Play policy for regulated-goods content)
          //   2. Language picker  (first thing users see in their own tongue)
          //   3. PantrieNav  (login → onboarding → deck → tour)
          // Hoisted out of PantrieNav so the WalkthroughOverlay can never race
          // with either gate. Each gate is its own composition root; nothing
          // else mounts until the gate clears.
          val hasConfirmedAge by localSettings.hasConfirmedAge.collectAsState()
          var hasPickedLocale by remember { mutableStateOf(localeManager.hasPicked()) }
          when {
            !hasConfirmedAge -> AgeGateScreen(
              onConfirmed = { localSettings.setHasConfirmedAge(true) },
              onDeclined = { finish() },
            )
            !hasPickedLocale -> app.pantrie.locale.LanguagePickerScreen(
              onPicked = { hasPickedLocale = true },
            )
            else -> PantrieNav(
              analytics = analytics,
              api = api,
              localSettings = localSettings,
              localeManager = localeManager,
              initialDeepLink = initialNavTarget,
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
  }

  companion object {
    /** Whitelist of routes the rescan/expiring notification PendingIntents may target.
     *  Anything else from EXTRA_NAV_TARGET is dropped to block intent-redress attacks. */
    private val ALLOWED_DEEP_LINK_ROUTES = setOf("pantry", "expiring", "shopping", "deck")
  }
}

/**
 * Toggle FLAG_SECURE for the current route. Applied to routes that display sensitive data
 * (auth, billing, account settings). Cooking-centric screens stay screenshottable so users
 * can share recipes.
 */
@Composable
fun SensitiveScreen(sensitive: Boolean, content: @Composable () -> Unit) {
  val view = LocalView.current
  DisposableEffect(sensitive, view) {
    val window = (view.context as? android.app.Activity)?.window
    if (sensitive) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    onDispose { if (sensitive) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
  }
  content()
}

// `anchorKey` ties a tab to a tour-step anchor; the NavigationBarItem reports its
// measured bounds under that key so the WalkthroughOverlay can spotlight the right tab.
// `iconRes` is a photorealistic PNG drawable from the brimm_nav_* set generated by
// backend/ingest/generate_brimm_images.cjs. Speakeater is SVG/emoji-free.
private data class Tab(
  val route: String,
  val label: String,
  val iconRes: Int,
  val anchorKey: String? = null,
)

// Boxed-button nav icon. Brass border + dark surface so the photorealistic PNG
// reads as a tappable affordance, not a floating image. Selected state pumps
// the border so the active tab is unambiguous. Speakeater is SVG/emoji-free.
@Composable
private fun NavIconBox(
  iconRes: Int,
  contentDescription: String,
  selected: Boolean,
) {
  val borderColor = if (selected)
    app.pantrie.ui.theme.BrassBright
  else
    app.pantrie.ui.theme.Rule
  val bgColor = if (selected)
    app.pantrie.ui.theme.Paper3
  else
    app.pantrie.ui.theme.Paper2
  androidx.compose.foundation.layout.Box(
    modifier = Modifier
      .size(48.dp)
      .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
      .background(bgColor)
      .border(1.dp, borderColor, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
      .padding(3.dp),
    contentAlignment = androidx.compose.ui.Alignment.Center,
  ) {
    androidx.compose.foundation.Image(
      painter = androidx.compose.ui.res.painterResource(iconRes),
      contentDescription = contentDescription,
      modifier = Modifier
        .size(38.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
      contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
  }
}

// Streamlined bottom nav: Home + Feed + You. Pantry/Shop/Plan live as quick-action
// buttons on the Home screen to reclaim the half-inch of wasted bottom space.
private val BASE_TABS = listOf(
  Tab("deck", "Culinary", app.pantrie.R.drawable.brimm_nav_culinary, anchorKey = TourAnchors.NAV_CULINARY),
  Tab("community", "Feed", app.pantrie.R.drawable.brimm_nav_feed, anchorKey = TourAnchors.NAV_FEED),
  Tab("settings", "You", app.pantrie.R.drawable.brimm_nav_you, anchorKey = TourAnchors.NAV_YOU),
)

private val MIXOLOGY_TAB = Tab(
  "mixology",
  "Mixology",
  app.pantrie.R.drawable.brimm_nav_mixology,
  anchorKey = TourAnchors.NAV_MIXOLOGY,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PantrieNav(
  analytics: Analytics,
  api: PantrieApi,
  localSettings: LocalSettingsStore,
  localeManager: app.pantrie.locale.LocaleManager,
  initialDeepLink: String? = null,
) {
  val nav = rememberNavController()
  val backStack by nav.currentBackStackEntryAsState()
  val currentRoute = backStack?.destination?.route

  // Mixology tab is opt-in (Settings → Show Mixology). Inserted between Tonight
  // and Shop so it reads as the second "what to make" tab.
  val showMixology by localSettings.showMixology.collectAsState()
  val tabs = remember(showMixology) {
    if (showMixology) {
      BASE_TABS.toMutableList().apply { add(2, MIXOLOGY_TAB) }
    } else BASE_TABS
  }
  // Show bottom nav on every logged-in screen so users can always navigate home.
  // Hide only on login / onboarding / deep drill-downs that own the whole screen (scan, cook, etc).
  val fullScreenRoutes = setOf("lang_picker", "login", "onboarding", "scan", "receipt", "barcode")
  val showBottomBar = currentRoute != null
    && currentRoute !in fullScreenRoutes
    && !(currentRoute.startsWith("cook/"))

  LaunchedEffect(currentRoute) {
    analytics.setRoute(currentRoute)
    if (currentRoute != null) analytics.track("screen_view", mapOf("route" to currentRoute))
  }

  // Consume rescan/expiring deep-link once we're past the login gate.
  val deepLinkConsumed = remember { mutableStateOf(false) }
  LaunchedEffect(initialDeepLink, currentRoute) {
    if (!deepLinkConsumed.value && initialDeepLink != null && currentRoute != null && currentRoute != "login" && currentRoute != "onboarding") {
      deepLinkConsumed.value = true
      nav.navigate(initialDeepLink) {
        popUpTo("pantry") { saveState = true }
        launchSingleTop = true
      }
    }
  }

  var showFeedback by remember { mutableStateOf(false) }

  val appVm: AppStateViewModel = hiltViewModel()
  val expiringCount by appVm.expiringCount.collectAsState()
  val shoppingCount by appVm.shoppingCount.collectAsState()

  // First-launch guided walkthrough — overlays the whole UI when triggered. Gated on
  // route below so it only fires once the user is past login + onboarding (otherwise
  // the spotlight references tabs/screens that aren't visible yet).
  val tourVm: WalkthroughViewModel = hiltViewModel()
  val tourState by tourVm.uiState.collectAsState()
  val tourAnchors by tourVm.anchors.collectAsState()

  // Push current route into the tour VM so its RouteIs/RouteIn triggers can auto-advance
  // when the user taps a highlighted tab/tile and the navigation actually happens. Only
  // push routes that are tour-eligible (past login/onboarding) so the early auth-flow
  // navigation doesn't accidentally advance the tour state machine before the overlay
  // is even rendered. Push null while ineligible so any trigger watching for a stale
  // pre-tour route doesn't fire on hydrate.
  LaunchedEffect(currentRoute) {
    val eligible = currentRoute != null
      && currentRoute != "lang_picker"
      && currentRoute != "login"
      && currentRoute != "onboarding"
    tourVm.reportCurrentRoute(if (eligible) currentRoute else null)
  }

  // Tour-driven navigation: when the tour starts (or replays from Settings) it requests
  // /deck so step 1's Pantry-tile spotlight has a target to anchor against.
  LaunchedEffect(Unit) {
    tourVm.navigationRequests.collect { route ->
      if (currentRoute != route && currentRoute != "login" && currentRoute != "onboarding") {
        nav.navigate(route) {
          launchSingleTop = true
          popUpTo(nav.graph.startDestinationId) { saveState = true }
          restoreState = true
        }
      }
    }
  }

  // Draggable feedback icon position — persists across config changes and process death.
  // Offsets are applied relative to the default bottom-right anchor.
  var fbOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
  var fbOffsetY by rememberSaveable { mutableFloatStateOf(0f) }

  // Theme the bottom nav + FAB for Mixology. Whole app is now dark — Mixology gets a brass
  // accent on selected items so it still reads distinct from the other tabs.
  val isMixology = currentRoute == "mixology"
  val darkBg = app.pantrie.ui.theme.Paper2
  val goldAccent = app.pantrie.ui.theme.BrassBright
  val mutedGold = app.pantrie.ui.theme.InkFaint
  val navBg = if (isMixology) darkBg
    else MaterialTheme.colorScheme.surface
  val navItemSelected = if (isMixology) goldAccent
    else MaterialTheme.colorScheme.onSurface
  val navItemUnselected = if (isMixology) mutedGold
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

  Box(modifier = Modifier.fillMaxSize()) {
  Scaffold(
    bottomBar = {
      if (showBottomBar) {
        NavigationBar(containerColor = navBg) {
          tabs.forEach { tab ->
            // Capture in a local so the smart-cast survives into the onGloballyPositioned
            // lambda below (a property read on `tab` would not smart-cast across the
            // lambda boundary).
            val anchorKey = tab.anchorKey
            NavigationBarItem(
              modifier = if (anchorKey != null) {
                Modifier.onGloballyPositioned { coords ->
                  // Report this tab's bounds (in window coords) to the walkthrough
                  // registry. The overlay reads these to draw the spotlight on the
                  // exact tab the current step is meant to highlight.
                  tourVm.reportAnchor(anchorKey, coords.boundsInWindow())
                }
              } else Modifier,
              selected = currentRoute == tab.route,
              onClick = {
                analytics.track("tab_switched", mapOf("to" to tab.route))
                nav.navigate(tab.route) {
                  popUpTo("pantry") { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
                appVm.refresh() // refresh badge whenever user changes tab
              },
              icon = {
                // Photorealistic PNG nav icon, framed in a brass-bordered button-box
                // so each tab reads as a tappable affordance. Speakeater is SVG/emoji-free.
                if (tab.route == "shopping" && shoppingCount > 0) {
                  val badgeColor = if (expiringCount > 0)
                    app.pantrie.ui.theme.Terracotta
                  else
                    app.pantrie.ui.theme.Olive
                  androidx.compose.material3.BadgedBox(
                    badge = {
                      androidx.compose.material3.Badge(
                        containerColor = badgeColor,
                        contentColor = app.pantrie.ui.theme.Ink,
                      ) { Text("$shoppingCount") }
                    },
                  ) {
                    NavIconBox(
                      iconRes = tab.iconRes,
                      contentDescription = tab.label,
                      selected = currentRoute?.startsWith(tab.route) == true,
                    )
                  }
                } else {
                  NavIconBox(
                    iconRes = tab.iconRes,
                    contentDescription = tab.label,
                    selected = currentRoute?.startsWith(tab.route) == true,
                  )
                }
              },
              label = { Text(tab.label, maxLines = 1, softWrap = false) },
              colors = NavigationBarItemDefaults.colors(
                selectedIconColor = navItemSelected,
                selectedTextColor = navItemSelected,
                unselectedIconColor = navItemUnselected,
                unselectedTextColor = navItemUnselected,
                indicatorColor = if (isMixology) goldAccent.copy(alpha = 0.15f)
                  else MaterialTheme.colorScheme.secondaryContainer,
              ),
            )
          }
        }
      }
    },
  ) { padding ->
    // Language picker now lives at MainActivity level (hoisted out of nav)
    // so it can't race with the WalkthroughOverlay or any other PantrieNav
    // composable. By the time we reach this NavHost, the user has already
    // picked a locale, so login is always the start destination.
    NavHost(
      navController = nav,
      startDestination = "login",
      modifier = Modifier.padding(padding),
    ) {
      composable("login") {
        val scope = rememberCoroutineScope()
        LoginScreen(onLoggedIn = {
          analytics.track("login_success")
          scope.launch {
            val onboarded = runCatching { api.getPreferences().onboarded }.getOrDefault(false)
            val dest = if (onboarded) "deck" else "onboarding"
            nav.navigate(dest) { popUpTo("login") { inclusive = true } }
          }
        })
      }
      composable("onboarding") {
        OnboardingScreen(onDone = {
          nav.navigate("deck") { popUpTo("onboarding") { inclusive = true } }
        })
      }
      composable("pantry") {
        PantryScreen(
          onScan = { nav.navigate("scan") },
          onBarcode = { nav.navigate("barcode") },
          onReceipt = { nav.navigate("receipt") },
        )
      }
      composable("deck") {
        DeckScreen(
          onOpenRecipe = { nav.navigate("recipe/$it") },
          onStartCook = { nav.navigate("cook/$it") },
          onOpenSaved = { nav.navigate("saved") },
          onOpenPantry = { nav.navigate("pantry") },
          onOpenShopping = { nav.navigate("shopping") },
          onOpenPlan = { nav.navigate("plan") },
          onOpenSearch = { nav.navigate("search?type=food") },
          onOpenPaywall = { nav.navigate("paywall") },
          onContributePhoto = { rid -> nav.navigate("recipe/$rid/contribute-photo") },
        )
      }
      composable("mixology") {
        MixologyScreen(
          onOpenSaved = { nav.navigate("saved") },
          onScanBar = { nav.navigate("scan_bar") },
          onOpenShopping = { nav.navigate("shopping") },
          onOpenPlan = { nav.navigate("plan") },
          onOpenSearch = { nav.navigate("search?type=cocktail") },
          onOpenPaywall = { nav.navigate("paywall") },
          // "Photo your pour" → contribute-photo flow (system camera + gallery
          // + R2 upload + admin review). Replaces the old `submit-photo-recipe`
          // route which was the Pro recipe-from-photo extractor — wrong screen
          // for "I made this pour" intent and gallery-only on top of that.
          onSubmitDrinkPhoto = { rid -> nav.navigate("recipe/$rid/contribute-photo") },
        )
      }
      composable("paywall") {
        app.pantrie.billing.PaywallScreen(onClose = { nav.popBackStack() })
      }
      composable(
        route = "search?type={type}",
        arguments = listOf(navArgument("type") {
          type = NavType.StringType
          defaultValue = ""
        }),
      ) { back ->
        val type = back.arguments?.getString("type")?.takeIf { it.isNotBlank() }
        SearchSheet(
          contentType = type,
          onBack = { nav.popBackStack() },
          onPick = { id ->
            nav.navigate("recipe/$id") {
              popUpTo("search?type={type}") { inclusive = true }
            }
          },
        )
      }
      composable("saved") {
        SavedScreen(
          onBack = { nav.popBackStack() },
          onOpenRecipe = { nav.navigate("recipe/$it") },
          onStartCook = { nav.navigate("cook/$it") },
          onOpenLibrary = { nav.navigate("library") },
        )
      }
      composable("submit") {
        SubmitRecipeScreen(onBack = { nav.popBackStack() })
      }
      composable(
        route = "recipe/{id}/contribute-photo",
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
      ) { back ->
        val rid = back.arguments?.getString("id").orEmpty()
        app.pantrie.feature.contribute.ContributeRecipePhotoScreen(
          recipeId = rid,
          onBack = { nav.popBackStack() },
        )
      }
      composable("submit-photo-recipe") {
        // Pro-only photo-to-recipe flow. Pro gating handled inside the screen so the
        // route is reachable from anywhere; the gate decides whether to show the picker
        // or the ProUpgradeCard.
        PhotoToRecipeScreen(
          onBack = { nav.popBackStack() },
          onSubmitted = {
            // Pop back to MySubmissions so the user immediately sees the new pending row.
            nav.popBackStack()
          },
        )
      }
      composable("my_submissions") {
        MySubmissionsScreen(
          onBack = { nav.popBackStack() },
          onNewSubmission = { nav.navigate("submit-photo-recipe") },
        )
      }
      // Library: three-level Books → Chapters → Recipes. Replaces the
      // saves/submissions surfaces that used to be buried under Settings.
      composable("library") {
        LibraryScreen(
          onBack = { nav.popBackStack() },
          onOpenBook = { id -> nav.navigate("library/book/$id") },
        )
      }
      composable(
        route = "library/book/{bookId}",
        arguments = listOf(androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.StringType }),
      ) { entry ->
        val bookId = entry.arguments?.getString("bookId") ?: return@composable
        val ctx = androidx.compose.ui.platform.LocalContext.current
        // VM at this Composable scope so the export callback can reach it.
        val libVm: app.pantrie.feature.library.LibraryViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        BookDetailScreen(
          bookId = bookId,
          onBack = { nav.popBackStack() },
          onOpenRecipe = { rid -> nav.navigate("recipe/$rid") },
          onExport = { id, fmt ->
            // Mint a 5-minute share token, then open the URL via system browser.
            // The token authorizes the export through query param so the browser
            // (which does not carry our JWT) can still download owner-private
            // books. Token is HMAC-signed with JWT_SECRET, single-use de facto.
            val baseUrl = app.pantrie.BuildConfig.API_BASE_URL
            libVm.exportWithShareToken(id, fmt, baseUrl) { url ->
              val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
              try { ctx.startActivity(intent) } catch (_: Throwable) {}
            }
          },
          vm = libVm,
        )
      }
      composable("shopping") { ShoppingScreen() }
      composable("plan") {
        PlanScreen(onOpenMealPrep = { nav.navigate("mealprep") })
      }
      composable("community") {
        CommunityScreen(onOpenRecipe = { nav.navigate("recipe/$it") })
      }
      composable("settings") {
        SettingsScreen(
          onBack = { nav.popBackStack() },
          onRedoOnboarding = {
            nav.navigate("onboarding") { popUpTo("settings") { inclusive = true } }
          },
          onOpenSubmissions = { nav.navigate("my_submissions") },
          onOpenImportLinks = { nav.navigate("import_links") },
          onOpenPriceDemo = { nav.navigate("price_demo") },
        )
      }
      // Internal-only mockup screen for the price-comparison + place-order
      // partner pitch. Wired only in debug builds; the Settings entry that
      // navigates here is gated on BuildConfig.DEBUG so the route is
      // unreachable in release.
      composable("price_demo") {
        app.pantrie.feature.pricedemo.PriceComparisonMockScreen(
          onBack = { nav.popBackStack() },
        )
      }
      // TikTok / YouTube link-import flow. Pro-gated server-side (returns 402
      // with upsell:true if not Pro). The share-intent handler in onCreate
      // also routes here, pre-filled, so users can share-sheet directly from
      // TikTok into the import.
      composable(
        route = "import_links?prefill={prefill}",
        arguments = listOf(navArgument("prefill") {
          type = NavType.StringType
          nullable = true
          defaultValue = null
        }),
      ) { back ->
        val prefill = back.arguments?.getString("prefill")?.let {
          // Decoded back from the URL-encoded form set by the share-intent handler.
          runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull()
        }
        app.pantrie.feature.importlinks.ImportLinksScreen(
          onJobReady = { jobId -> nav.navigate("import_review/$jobId") },
          onCancel = { nav.popBackStack() },
          initialUrl = prefill,
        )
      }
      composable(
        route = "import_review/{jobId}",
        arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
      ) { back ->
        val jobId = back.arguments?.getString("jobId") ?: return@composable
        app.pantrie.feature.importlinks.ImportReviewScreen(
          jobId = jobId,
          // Submitted recipes land in /me/submissions as 'pending' — take the
          // user there so they immediately see where their parsed data went.
          // Clears the import stack so back navigates to Settings, not the
          // empty review screen.
          onClose = {
            nav.navigate("my_submissions") {
              popUpTo("settings") { inclusive = false }
            }
          },
        )
      }
      composable("mealprep") { MealPrepScreen(onBack = { nav.popBackStack() }) }
      composable("scan") { ScanScreen(onDone = { nav.popBackStack() }, initialMode = ScanMode.PantryPhoto) }
      composable("receipt") { ScanScreen(onDone = { nav.popBackStack() }, initialMode = ScanMode.Receipt) }
      composable("scan_bar") { ScanScreen(onDone = { nav.popBackStack() }, initialMode = ScanMode.BarShelf) }
      composable("barcode") { BarcodeScreen(onDone = { nav.popBackStack() }) }
      composable(
        route = "recipe/{recipeId}",
        arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
      ) { back ->
        val id = back.arguments?.getString("recipeId") ?: return@composable
        RecipeDetailScreen(recipeId = id, onBack = { nav.popBackStack() })
      }
      composable(
        route = "cook/{recipeId}",
        arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
      ) { back ->
        val id = back.arguments?.getString("recipeId") ?: return@composable
        CookModeScreen(recipeId = id, onExit = { nav.popBackStack() })
      }
    }
  }

  // Draggable beta feedback icon — floats above everything, repositionable by drag.
  // Default anchor: bottom-right, just above the nav bar.
  if (currentRoute != null && currentRoute != "login" && currentRoute != "onboarding") {
    SmallFloatingActionButton(
      onClick = { analytics.track("beta_feedback_opened"); showFeedback = true },
      containerColor = if (isMixology) darkBg else MaterialTheme.colorScheme.secondaryContainer,
      contentColor = if (isMixology) goldAccent else androidx.compose.material3.contentColorFor(MaterialTheme.colorScheme.secondaryContainer),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = if (showBottomBar) 96.dp else 24.dp)
        .offset { IntOffset(fbOffsetX.roundToInt(), fbOffsetY.roundToInt()) }
        .pointerInput(Unit) {
          detectDragGestures { change, drag ->
            change.consume()
            fbOffsetX += drag.x
            fbOffsetY += drag.y
          }
        },
    ) { Icon(Icons.Outlined.Feedback, contentDescription = "Feedback") }
  }

  // First-launch walkthrough overlay — sits on top of everything (including the FAB
  // and bottom nav). Only renders past login/onboarding so the welcome card shows up
  // against the real app, not the auth screen. Also excludes lang_picker — overlay
  // would render OVER the picker and both got tap-cancelled by each other.
  val tourEligibleRoute = currentRoute != null
    && currentRoute != "lang_picker"
    && currentRoute != "login"
    && currentRoute != "onboarding"
  if (tourEligibleRoute && tourState.visible) {
    WalkthroughOverlay(
      state = tourState,
      anchors = tourAnchors,
      onNext = { tourVm.next() },
      onSkip = { tourVm.skip() },
      onEnterMiniTour = { tourVm.enterMiniTour(it) },
      onSelfExplore = { tourVm.selfExplore() },
    )
  }
  } // end outer Box

  if (showFeedback) {
    BetaFeedbackSheet(
      currentRoute = currentRoute,
      onDismiss = { showFeedback = false },
    )
  }
}
