package app.pantrie

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import app.pantrie.feature.mealprep.MealPrepScreen
import app.pantrie.feature.notifications.NotificationScheduler
import app.pantrie.feature.notifications.RescanWorker
import app.pantrie.feature.onboarding.OnboardingScreen
import app.pantrie.feature.pantry.PantryScreen
import app.pantrie.feature.plan.PlanScreen
import app.pantrie.feature.recipe.RecipeDetailScreen
import app.pantrie.feature.saved.SavedScreen
import app.pantrie.feature.submit.MySubmissionsScreen
import app.pantrie.feature.submit.SubmitRecipeScreen
import app.pantrie.feature.scan.ScanMode
import app.pantrie.feature.scan.ScanScreen
import app.pantrie.feature.settings.SettingsScreen
import app.pantrie.feature.shopping.ShoppingScreen
import app.pantrie.network.PantrieApi
import app.pantrie.ui.theme.PantrieTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var analytics: Analytics
  @Inject lateinit var api: PantrieApi

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    analytics.track("app_opened")
    NotificationScheduler.schedule(this)

    val initialNavTarget = intent?.getStringExtra(RescanWorker.EXTRA_NAV_TARGET)

    setContent {
      PantrieTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          PantrieNav(analytics = analytics, api = api, initialDeepLink = initialNavTarget)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
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

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
  Tab("pantry", "Pantry", Icons.Outlined.Kitchen),
  Tab("deck", "Tonight", Icons.Outlined.Explore),
  Tab("shopping", "Shop", Icons.Outlined.ShoppingCart),
  Tab("plan", "Plan", Icons.Outlined.CalendarMonth),
  Tab("community", "Feed", Icons.Outlined.Forum),
  Tab("settings", "You", Icons.Outlined.Settings),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PantrieNav(analytics: Analytics, api: PantrieApi, initialDeepLink: String? = null) {
  val nav = rememberNavController()
  val backStack by nav.currentBackStackEntryAsState()
  val currentRoute = backStack?.destination?.route
  val showBottomBar = currentRoute in TABS.map { it.route }

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

  // Draggable feedback icon position — persists across config changes and process death.
  // Offsets are applied relative to the default bottom-right anchor.
  var fbOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
  var fbOffsetY by rememberSaveable { mutableFloatStateOf(0f) }

  Box(modifier = Modifier.fillMaxSize()) {
  Scaffold(
    bottomBar = {
      if (showBottomBar) {
        NavigationBar {
          TABS.forEach { tab ->
            NavigationBarItem(
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
                // Shop badge: count of items on the shopping list. Colored red when
                // pantry items are expiring (you should also buy replacements soon).
                if (tab.route == "shopping" && shoppingCount > 0) {
                  val badgeColor = if (expiringCount > 0)
                    androidx.compose.ui.graphics.Color(0xFFB04A3C)          // Terracotta — something's expiring too
                  else
                    androidx.compose.ui.graphics.Color(0xFF7A8450)          // Olive — just a shopping count
                  androidx.compose.material3.BadgedBox(
                    badge = {
                      androidx.compose.material3.Badge(
                        containerColor = badgeColor,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                      ) { Text("$shoppingCount") }
                    },
                  ) { Icon(tab.icon, contentDescription = tab.label) }
                } else {
                  Icon(tab.icon, contentDescription = tab.label)
                }
              },
              label = { Text(tab.label, maxLines = 1, softWrap = false) },
            )
          }
        }
      }
    },
  ) { padding ->
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
            val dest = if (onboarded) "pantry" else "onboarding"
            nav.navigate(dest) { popUpTo("login") { inclusive = true } }
          }
        })
      }
      composable("onboarding") {
        OnboardingScreen(onDone = {
          nav.navigate("pantry") { popUpTo("onboarding") { inclusive = true } }
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
        )
      }
      composable("saved") {
        SavedScreen(
          onBack = { nav.popBackStack() },
          onOpenRecipe = { nav.navigate("recipe/$it") },
          onStartCook = { nav.navigate("cook/$it") },
        )
      }
      composable("submit") {
        SubmitRecipeScreen(onBack = { nav.popBackStack() })
      }
      composable("my_submissions") {
        MySubmissionsScreen(
          onBack = { nav.popBackStack() },
          onNewSubmission = { nav.navigate("submit") },
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
        )
      }
      composable("mealprep") { MealPrepScreen(onBack = { nav.popBackStack() }) }
      composable("scan") { ScanScreen(onDone = { nav.popBackStack() }, initialMode = ScanMode.PantryPhoto) }
      composable("receipt") { ScanScreen(onDone = { nav.popBackStack() }, initialMode = ScanMode.Receipt) }
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
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
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
  } // end outer Box

  if (showFeedback) {
    BetaFeedbackSheet(
      currentRoute = currentRoute,
      onDismiss = { showFeedback = false },
    )
  }
}
