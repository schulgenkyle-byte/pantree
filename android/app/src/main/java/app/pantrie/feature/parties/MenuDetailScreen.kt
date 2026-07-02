package app.pantrie.feature.parties

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.ui.theme.BrassBright
import app.pantrie.ui.theme.BrassDeep
import app.pantrie.ui.theme.Ink
import app.pantrie.ui.theme.InkFaint
import app.pantrie.ui.theme.InkSoft
import app.pantrie.ui.theme.Mono
import app.pantrie.ui.theme.Olive
import app.pantrie.ui.theme.Paper
import app.pantrie.ui.theme.Paper2
import app.pantrie.ui.theme.Paper3
import app.pantrie.ui.theme.Rule
import app.pantrie.ui.theme.SerifBody
import app.pantrie.ui.theme.SerifDisplay
import kotlinx.coroutines.launch

private enum class DetailTab(val label: String) {
  THE_MYSTERY("Mystery"),
  THE_NIGHT("The Night"),
  DRINKS("Drinks"),
  FOOD("Food"),
  SHOPPING("Shopping"),
  NOTES("Notes"),
}

/** Tab row for a menu — mystery menus get the Mystery tab as a prefix. */
private fun tabsFor(menu: PartyMenu): List<DetailTab> =
  if (menu.isMystery) DetailTab.values().toList()
  else DetailTab.values().filterNot { it == DetailTab.THE_MYSTERY }

/**
 * Full menu detail. Hero image at top, narrative scroll below with five
 * sticky tabs that smooth-scroll to the corresponding section. Locked state
 * dims and adds a sticky paywall overlay across the lower third.
 */
@Composable
fun MenuDetailScreen(
  menuId: String,
  onBack: () -> Unit,
  onHostGame: () -> Unit = {},
  vm: PartiesViewModel = hiltViewModel(),
) {
  val menu = remember(menuId) { findMenu(menuId) } ?: run {
    LaunchedEffect(Unit) { onBack() }
    return
  }
  val isPro by vm.entitlement.isPro.collectAsState()
  val redeemed by vm.redeemedMenuIds.collectAsState()
  val unlocked = isPro || menu.id in redeemed || PartiesViewModel.ALL_WILDCARD in redeemed

  var showPaywall by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()

  // Section anchors — each section's first item gets this key, scroll target
  // is the index of that key in the LazyColumn.
  val sectionIndices = remember(menu, unlocked) {
    // Items in render order. Header (hero) is always index 0.
    // When LOCKED we only render the hero + lede + paywall promo; no section anchors.
    if (!unlocked) {
      emptyMap()
    } else {
      // Lazy items: hero(0), lede(1), then per-section header + body items.
      // Mystery menus insert THE_MYSTERY (hook + cast.size + playbook + reveal = 3 + cast.size body items)
      // before THE_NIGHT.
      mutableMapOf<DetailTab, Int>().apply {
        var idx = 2 // hero + lede
        if (menu.isMystery) {
          val castCount = menu.cast?.size ?: 0
          put(DetailTab.THE_MYSTERY, idx)
          // header + hook + cast + playbook + reveal
          idx += 1 + 1 + castCount + 1 + 1
        }
        put(DetailTab.THE_NIGHT, idx); idx += 1 + menu.timeline.size
        put(DetailTab.DRINKS, idx); idx += 1 + menu.drinks.size
        put(DetailTab.FOOD, idx); idx += 1 + menu.food.size
        put(DetailTab.SHOPPING, idx); idx += 2 // header + grouped list + total
        put(DetailTab.NOTES, idx)
      }
    }
  }

  // Hide the tab bar until the user scrolls past the hero (index 0).
  val showStickyTabs by remember {
    derivedStateOf { listState.firstVisibleItemIndex >= 1 }
  }
  // Track which tab is "current" based on first fully-visible item.
  val currentTab by remember(sectionIndices) {
    derivedStateOf {
      val first = listState.firstVisibleItemIndex
      sectionIndices.entries
        .filter { first >= it.value }
        .maxByOrNull { it.value }
        ?.key
        ?: DetailTab.THE_NIGHT
    }
  }

  Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 64.dp),
      ) {
        item("hero") { HeroPanel(menu = menu, onBack = onBack) }
        item("lede") {
          Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Text(
              text = menu.description,
              style = MaterialTheme.typography.bodyLarge.copy(
                color = Ink,
                fontFamily = SerifBody,
                fontSize = 17.sp,
                lineHeight = 28.sp,
              ),
            )
            Spacer(Modifier.height(20.dp))
            DottedGoldRule(modifier = Modifier.fillMaxWidth())
          }
        }
        if (unlocked) {
          // THE MYSTERY — only for mystery menus. Comes before THE_NIGHT.
          if (menu.isMystery) {
            item("host_clarification") {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(
                  "1 HOST · UP TO 8 GUESTS · FREE TO JOIN",
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = app.pantrie.ui.theme.BrassBright,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    letterSpacing = 2.4.sp,
                    fontWeight = FontWeight.SemiBold,
                  ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  "Generate a 4-letter code, text it to your table. They open the free app and join.",
                  style = MaterialTheme.typography.bodySmall.copy(
                    color = app.pantrie.ui.theme.InkSoft,
                    fontFamily = SerifBody,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                  ),
                  textAlign = TextAlign.Center,
                )
              }
            }
            item("host_game_cta") {
              androidx.compose.material3.Button(
                onClick = onHostGame,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 24.dp, vertical = 12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                  containerColor = app.pantrie.ui.theme.Terracotta,
                  contentColor = Paper,
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
              ) {
                Text(
                  "HOST A MYSTERY NIGHT",
                  style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = Mono,
                    letterSpacing = 2.8.sp,
                    fontWeight = FontWeight.SemiBold,
                  ),
                  modifier = Modifier.padding(vertical = 6.dp),
                )
              }
            }
            item("mystery_header") {
              SectionHeader(eyebrow = "THE MYSTERY", title = "A cast, a hook, a reveal")
            }
            item("mystery_hook") {
              MysteryHookBlock(hook = menu.mysteryHook.orEmpty())
            }
            menu.cast?.forEach { character ->
              item("cast_${character.name}") {
                CastCard(character = character)
              }
            }
            item("mystery_playbook") {
              HostPlaybookBlock(playbook = menu.hostPlaybook.orEmpty())
            }
            item("mystery_reveal") {
              RevealBlock(reveal = menu.mysteryReveal.orEmpty())
            }
          }
          // THE NIGHT
          item("the_night_header") {
            SectionHeader(eyebrow = "THE NIGHT", title = "Host timeline")
          }
          items(menu.timeline) { step ->
            TimelineRow(step = step)
          }
          // DRINKS
          item("drinks_header") {
            SectionHeader(eyebrow = "DRINKS", title = "Five cocktails")
          }
          items(menu.drinks) { drink ->
            DrinkRow(drink = drink)
          }
          // FOOD
          item("food_header") {
            SectionHeader(eyebrow = "FOOD", title = "Six small plates")
          }
          items(menu.food) { plate ->
            PlateRow(plate = plate)
          }
          // SHOPPING
          item("shopping_header") {
            SectionHeader(eyebrow = "SHOPPING", title = "Grouped by aisle")
          }
          item("shopping_body") {
            ShoppingBlock(items = menu.shoppingList, total = menu.shoppingTotal)
          }
          // NOTES
          item("notes") {
            NotesBlock(menu = menu)
          }
        } else {
          // Locked-state lede repeats the paywall promo because the user hasn't
          // unlocked yet.
          item("locked_promo") {
            LockedPromo(menu = menu, onUnlockTap = { showPaywall = true })
          }
        }
      }

      // Sticky tab bar — only when unlocked + scrolled past hero
      if (unlocked && showStickyTabs) {
        StickyTabRow(
          tabs = tabsFor(menu),
          current = currentTab,
          onTabSelected = { tab ->
            val target = sectionIndices[tab] ?: return@StickyTabRow
            scope.launch { listState.animateScrollToItem(target) }
          },
        )
      }

      // Locked-state sticky paywall band across the lower third
      if (!unlocked) {
        StickyPaywallBand(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(),
          onUnlockTap = { showPaywall = true },
        )
      }
    }

    if (showPaywall) {
      PaywallSheet(
        menuTitle = menu.title,
        onDismiss = { showPaywall = false },
        onRedeemed = { _ -> showPaywall = false },
        vm = vm,
        isMystery = menu.isMystery,
      )
    }
  }
}

@Composable
private fun HeroPanel(menu: PartyMenu, onBack: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(380.dp),
  ) {
    MenuHero(
      menuId = menu.id,
      modifier = Modifier.fillMaxSize(),
      fallbackTitle = menu.title,
      fallbackYear = menu.eraYear,
    )
    // Dark scrim on the bottom half so the title is legible against any image.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              Paper.copy(alpha = 0f),
              Paper.copy(alpha = 0f),
              Paper.copy(alpha = 0.65f),
              Paper.copy(alpha = 0.95f),
            ),
          ),
        ),
    )
    // Back arrow top-left.
    IconButton(
      onClick = onBack,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(top = 24.dp, start = 12.dp)
        .size(40.dp)
        .background(Paper3.copy(alpha = 0.75f), RoundedCornerShape(20.dp)),
    ) {
      Icon(
        imageVector = Icons.Outlined.ArrowBack,
        contentDescription = "Back",
        tint = Ink,
      )
    }
    // Title + era line bottom-left.
    Column(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
    ) {
      Text(
        text = menu.title,
        style = MaterialTheme.typography.displayMedium.copy(
          color = Ink,
          fontFamily = SerifDisplay,
          fontStyle = FontStyle.Italic,
          fontWeight = FontWeight.Normal,
          fontSize = 36.sp,
          lineHeight = 42.sp,
        ),
      )
      Spacer(Modifier.height(10.dp))
      Text(
        text = "${menu.eraCity.uppercase()} · ${menu.eraYear} · ${menu.eraSourceLine.uppercase()}",
        style = MaterialTheme.typography.labelMedium.copy(
          color = BrassBright,
          fontFamily = Mono,
          fontSize = 10.sp,
          letterSpacing = 2.4.sp,
        ),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = "${menu.guestCount.uppercase()} GUESTS · ${menu.duration.uppercase()}",
        style = MaterialTheme.typography.labelSmall.copy(
          color = InkSoft,
          fontFamily = Mono,
          fontSize = 9.sp,
          letterSpacing = 2.0.sp,
        ),
      )
    }
  }
}

@Composable
private fun StickyTabRow(
  tabs: List<DetailTab>,
  current: DetailTab,
  onTabSelected: (DetailTab) -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Paper2,
    shadowElevation = 4.dp,
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        tabs.forEach { tab ->
          val selected = tab == current
          Box(
            modifier = Modifier
              .clickable { onTabSelected(tab) }
              .padding(horizontal = 8.dp, vertical = 6.dp),
          ) {
            Text(
              text = tab.label,
              style = MaterialTheme.typography.labelMedium.copy(
                color = if (selected) BrassBright else InkSoft,
                fontFamily = Mono,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
              ),
            )
          }
        }
      }
      DottedGoldRule(modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun SectionHeader(eyebrow: String, title: String) {
  Column(
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
  ) {
    Text(
      text = eyebrow,
      style = MaterialTheme.typography.labelMedium.copy(
        color = BrassBright,
        fontFamily = Mono,
        fontSize = 10.sp,
        letterSpacing = 3.0.sp,
      ),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.headlineMedium.copy(
        color = Ink,
        fontFamily = SerifDisplay,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp,
      ),
    )
    Spacer(Modifier.height(12.dp))
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun TimelineRow(step: TimelineStep) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 10.dp),
  ) {
    // Left rail with a brass dot
    Column(
      modifier = Modifier.width(40.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .background(BrassBright, RoundedCornerShape(4.dp)),
      )
      Box(
        modifier = Modifier
          .padding(top = 4.dp)
          .width(1.dp)
          .height(60.dp)
          .background(Rule),
      )
    }
    Column(modifier = Modifier.padding(start = 4.dp)) {
      Text(
        text = step.timeLabel.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
          color = BrassBright,
          fontFamily = Mono,
          fontSize = 10.sp,
          letterSpacing = 2.0.sp,
        ),
      )
      Spacer(Modifier.height(8.dp))
      step.actions.forEach { action ->
        Text(
          text = "· $action",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = Ink,
            fontFamily = SerifBody,
            fontSize = 15.sp,
            lineHeight = 22.sp,
          ),
          modifier = Modifier.padding(vertical = 3.dp),
        )
      }
    }
  }
}

@Composable
private fun DrinkRow(drink: MenuDrink) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 14.dp),
  ) {
    Text(
      text = drink.name,
      style = MaterialTheme.typography.titleLarge.copy(
        color = Ink,
        fontFamily = SerifDisplay,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
      ),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = drink.sourceLine.uppercase(),
      style = MaterialTheme.typography.labelSmall.copy(
        color = BrassBright,
        fontFamily = Mono,
        fontSize = 9.sp,
        letterSpacing = 1.8.sp,
      ),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      text = drink.historyNote,
      style = MaterialTheme.typography.bodyMedium.copy(
        color = InkSoft,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
        fontSize = 14.sp,
        lineHeight = 22.sp,
      ),
    )
    Spacer(Modifier.height(12.dp))
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Paper2, RoundedCornerShape(2.dp))
        .border(0.5.dp, Rule, RoundedCornerShape(2.dp))
        .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
      Text(
        text = drink.recipe,
        style = MaterialTheme.typography.bodyMedium.copy(
          color = Ink,
          fontFamily = SerifBody,
          fontSize = 14.sp,
          lineHeight = 22.sp,
        ),
      )
    }
  }
  DottedGoldRule(modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth())
}

@Composable
private fun PlateRow(plate: MenuPlate) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Text(
      text = plate.name,
      style = MaterialTheme.typography.titleMedium.copy(
        color = Ink,
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
      ),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = plate.description,
      style = MaterialTheme.typography.bodyMedium.copy(
        color = InkSoft,
        fontFamily = SerifBody,
        fontSize = 14.sp,
        lineHeight = 22.sp,
      ),
    )
  }
  Box(
    modifier = Modifier
      .padding(horizontal = 24.dp)
      .fillMaxWidth(),
  ) { DottedGoldRule(modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun ShoppingBlock(items: List<ShoppingItem>, total: String) {
  val grouped = remember(items) { items.groupBy { it.aisle } }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 8.dp),
  ) {
    Text(
      text = total,
      style = MaterialTheme.typography.bodyLarge.copy(
        color = BrassBright,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
        fontSize = 16.sp,
        lineHeight = 24.sp,
      ),
    )
    Spacer(Modifier.height(20.dp))
    grouped.forEach { (aisle, list) ->
      Text(
        text = aisle.name.uppercase().replace("_", " "),
        style = MaterialTheme.typography.labelMedium.copy(
          color = Olive,
          fontFamily = Mono,
          fontSize = 10.sp,
          letterSpacing = 2.2.sp,
        ),
      )
      Spacer(Modifier.height(8.dp))
      list.forEach { item ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium.copy(
              color = Ink,
              fontFamily = SerifBody,
              fontSize = 14.sp,
            ),
            modifier = Modifier.weight(1f),
          )
          if (item.qty.isNotBlank()) {
            Text(
              text = item.qty,
              style = MaterialTheme.typography.labelMedium.copy(
                color = InkFaint,
                fontFamily = Mono,
                fontSize = 11.sp,
              ),
            )
          }
        }
      }
      Spacer(Modifier.height(14.dp))
    }
  }
}

@Composable
private fun NotesBlock(menu: PartyMenu) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 24.dp),
  ) {
    Text(
      text = "MUSIC",
      style = MaterialTheme.typography.labelMedium.copy(
        color = BrassBright,
        fontFamily = Mono,
        fontSize = 10.sp,
        letterSpacing = 3.0.sp,
      ),
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = menu.musicNote,
      style = MaterialTheme.typography.bodyMedium.copy(
        color = Ink,
        fontFamily = SerifBody,
        fontSize = 15.sp,
        lineHeight = 24.sp,
      ),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "Playlist: ${menu.playlistName}",
      style = MaterialTheme.typography.bodySmall.copy(
        color = InkFaint,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
        fontSize = 13.sp,
      ),
    )

    Spacer(Modifier.height(24.dp))
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))

    Text(
      text = "FAITHFUL AND MODERN-FLEXIBLE",
      style = MaterialTheme.typography.labelMedium.copy(
        color = BrassBright,
        fontFamily = Mono,
        fontSize = 10.sp,
        letterSpacing = 3.0.sp,
      ),
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = menu.faithfulNote,
      style = MaterialTheme.typography.bodyMedium.copy(
        color = Ink,
        fontFamily = SerifBody,
        fontSize = 15.sp,
        lineHeight = 24.sp,
      ),
    )

    Spacer(Modifier.height(28.dp))
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))

    Text(
      text = menu.citation,
      style = MaterialTheme.typography.bodySmall.copy(
        color = InkFaint,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
        fontSize = 12.sp,
        lineHeight = 18.sp,
      ),
    )
  }
}

@Composable
private fun LockedPromo(menu: PartyMenu, onUnlockTap: () -> Unit) {
  // Locked-state body. Tease the structure of what's inside without showing
  // any actual recipes. Period-correct framing: this is the cover of a menu
  // in the binder, not the menu itself.
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 24.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      LockChipLarge()
      Spacer(Modifier.width(12.dp))
      Column {
        Text(
          text = "INSIDE THIS MENU",
          style = MaterialTheme.typography.labelMedium.copy(
            color = BrassBright,
            fontFamily = Mono,
            fontSize = 10.sp,
            letterSpacing = 2.4.sp,
          ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = "Five drinks. Six plates. One night.",
          style = MaterialTheme.typography.titleMedium.copy(
            color = Ink,
            fontFamily = SerifDisplay,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
          ),
        )
      }
    }
    Spacer(Modifier.height(20.dp))
    LockedRow(label = "Cocktails", value = "${menu.drinks.size} drinks. Period-sourced. Cited.")
    LockedRow(label = "Food", value = "${menu.food.size} small plates. Era-correct. No food-blog adjectives.")
    LockedRow(label = "Host timeline", value = "${menu.timeline.size} stages, from the day before to zero hour.")
    LockedRow(label = "Shopping", value = "${menu.shoppingList.size} items, grouped by aisle.")
    LockedRow(label = "Music", value = "Era-matched playlist, ${menu.playlistName}.")
    LockedRow(label = "Notes", value = "Faithful and modern-flexible substitutions.")

    Spacer(Modifier.height(28.dp))
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(24.dp))

    // Promo line — gentle, period-voice.
    Text(
      text = "The Bee's Knees room at the Ritz Paris, 1929. Speakeasy opening night, Manhattan 1924. A Long Island estate in 1928. Forty-six more like them.",
      style = MaterialTheme.typography.bodyLarge.copy(
        color = InkSoft,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
        fontSize = 16.sp,
        lineHeight = 26.sp,
      ),
    )
    Spacer(Modifier.height(180.dp)) // space so sticky paywall doesn't overlap the citation
  }
}

@Composable
private fun LockedRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
  ) {
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelMedium.copy(
        color = InkFaint,
        fontFamily = Mono,
        fontSize = 10.sp,
        letterSpacing = 2.0.sp,
      ),
      modifier = Modifier.width(110.dp),
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(
        color = Ink,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun LockChipLarge() {
  Box(
    modifier = Modifier
      .size(40.dp)
      .background(BrassDeep, RoundedCornerShape(20.dp))
      .border(0.5.dp, BrassBright.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "·",
      style = MaterialTheme.typography.labelLarge.copy(
        color = BrassBright,
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
      ),
    )
  }
}

@Composable
private fun MysteryHookBlock(hook: String) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 4.dp),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Paper2, RoundedCornerShape(3.dp))
        .border(0.5.dp, BrassBright.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
        .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
      Text(
        text = hook,
        style = MaterialTheme.typography.bodyLarge.copy(
          color = Ink,
          fontFamily = SerifBody,
          fontStyle = FontStyle.Italic,
          fontSize = 16.sp,
          lineHeight = 26.sp,
        ),
      )
    }
  }
}

@Composable
private fun CastCard(character: MysteryCharacter) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 8.dp),
  ) {
    Surface(
      color = Paper3,
      modifier = Modifier
        .fillMaxWidth()
        .border(0.5.dp, Rule, RoundedCornerShape(3.dp)),
    ) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
          text = "CHARACTER",
          style = MaterialTheme.typography.labelSmall.copy(
            color = BrassBright,
            fontFamily = Mono,
            fontSize = 9.sp,
            letterSpacing = 2.4.sp,
          ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = character.name,
          style = MaterialTheme.typography.titleLarge.copy(
            color = Ink,
            fontFamily = SerifDisplay,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
          ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = character.role,
          style = MaterialTheme.typography.bodyMedium.copy(
            color = InkSoft,
            fontFamily = SerifBody,
            fontSize = 14.sp,
            lineHeight = 22.sp,
          ),
        )
        Spacer(Modifier.height(10.dp))
        // Brass dotted rule above secret
        DottedGoldRule(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
          text = "SECRET · GIVEN PRIVATELY",
          style = MaterialTheme.typography.labelSmall.copy(
            color = BrassDeep,
            fontFamily = Mono,
            fontSize = 9.sp,
            letterSpacing = 2.0.sp,
          ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = character.secret,
          style = MaterialTheme.typography.bodySmall.copy(
            color = InkSoft,
            fontFamily = SerifBody,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            lineHeight = 20.sp,
          ),
        )
      }
    }
  }
}

@Composable
private fun HostPlaybookBlock(playbook: String) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp),
  ) {
    Text(
      text = "HOST PLAYBOOK",
      style = MaterialTheme.typography.labelMedium.copy(
        color = BrassBright,
        fontFamily = Mono,
        fontSize = 10.sp,
        letterSpacing = 3.0.sp,
      ),
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = playbook,
      style = MaterialTheme.typography.bodyMedium.copy(
        color = Ink,
        fontFamily = SerifBody,
        fontSize = 14.sp,
        lineHeight = 22.sp,
      ),
    )
  }
}

@Composable
private fun RevealBlock(reveal: String) {
  var expanded by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Surface(
      color = Paper2,
      modifier = Modifier
        .fillMaxWidth()
        .border(0.5.dp, BrassBright.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
        .clickable { expanded = !expanded },
    ) {
      Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = if (expanded) "THE REVEAL" else "TAP TO REVEAL · HOST ONLY",
            style = MaterialTheme.typography.labelMedium.copy(
              color = BrassBright,
              fontFamily = Mono,
              fontSize = 10.sp,
              letterSpacing = 2.8.sp,
              fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.weight(1f),
          )
          Icon(
            imageVector = if (expanded) Icons.Outlined.ArrowBack else Icons.Outlined.ArrowBack,
            contentDescription = null,
            tint = BrassBright,
            modifier = Modifier
              .size(20.dp)
              .alpha(0f), // chevron substitute later; alpha-zero keeps row balanced
          )
        }
        if (expanded) {
          Spacer(Modifier.height(10.dp))
          Text(
            text = reveal,
            style = MaterialTheme.typography.bodyMedium.copy(
              color = Ink,
              fontFamily = SerifBody,
              fontStyle = FontStyle.Italic,
              fontSize = 14.sp,
              lineHeight = 22.sp,
            ),
          )
        }
      }
    }
  }
}

@Composable
private fun StickyPaywallBand(
  modifier: Modifier = Modifier,
  onUnlockTap: () -> Unit,
) {
  Box(
    modifier = modifier
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            Paper.copy(alpha = 0f),
            Paper.copy(alpha = 0.92f),
            Paper.copy(alpha = 1.0f),
          ),
        ),
      )
      .padding(horizontal = 16.dp)
      .padding(top = 32.dp, bottom = 16.dp),
  ) {
    Surface(
      color = Paper2,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, BrassBright.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
      ) {
        Text(
          text = "SPEAKEATER PRO",
          style = MaterialTheme.typography.labelMedium.copy(
            color = BrassBright,
            fontFamily = Mono,
            fontSize = 9.sp,
            letterSpacing = 2.4.sp,
          ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = "Unlock every menu, every Mystery Night, and the cocktail archive. Kickstarter backers can redeem a code instead.",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = Ink,
            fontFamily = SerifBody,
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            lineHeight = 20.sp,
          ),
        )
        Spacer(Modifier.height(12.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(BrassBright, RoundedCornerShape(3.dp))
            .clickable(onClick = onUnlockTap)
            .padding(vertical = 12.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "UNLOCK WITH PRO",
            style = MaterialTheme.typography.labelLarge.copy(
              color = Paper,
              fontFamily = Mono,
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp,
              letterSpacing = 2.8.sp,
            ),
          )
        }
      }
    }
  }
}

