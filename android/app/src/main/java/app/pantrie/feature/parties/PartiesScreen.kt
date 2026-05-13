package app.pantrie.feature.parties

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.BuildConfig
import app.pantrie.ui.theme.BrassBright
import app.pantrie.ui.theme.BrassDeep
import app.pantrie.ui.theme.Ink
import app.pantrie.ui.theme.InkFaint
import app.pantrie.ui.theme.InkSoft
import app.pantrie.ui.theme.Paper
import app.pantrie.ui.theme.Paper2
import app.pantrie.ui.theme.Paper3
import app.pantrie.ui.theme.Rule
import app.pantrie.ui.theme.Mono
import app.pantrie.ui.theme.SerifBody
import app.pantrie.ui.theme.SerifDisplay
import app.pantrie.ui.theme.Terracotta
import app.pantrie.ui.theme.TerracottaDeep

/**
 * Parties tab landing — a vertical-scroll grid of five Curate-a-Party menu cards.
 *
 * Each card is a hero-image-led editorial composition with title, era line, and
 * a lock chip. Tapping a card opens [MenuDetailScreen] for that menu.
 *
 * The whole tab is contained — no sticky top app bar, no FAB, no chrome. The
 * editorial register is the bar. The brass-bordered hairlines + Mono eyebrow
 * labels are the only chrome.
 */
@Composable
fun PartiesScreen(
  onOpenMenu: (menuId: String) -> Unit,
  onJoinGame: () -> Unit = {},
  vm: PartiesViewModel = hiltViewModel(),
) {
  val isPro by vm.entitlement.isPro.collectAsState()
  val redeemed by vm.redeemedMenuIds.collectAsState()

  // Accordion state — every section (including the two READY-NOW headers) is
  // accordion-collapsible. Defaults: PARTY + MYSTERY expanded so screen reads
  // alive on first open; cohorts collapsed so the grid stays scannable.
  val expanded = remember {
    mutableStateMapOf<String, Boolean>().apply {
      put(SECTION_PARTY, true)
      put(SECTION_MYSTERY, true)
      Cohort.values().forEach { put(it.name, false) }
    }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = Paper,
  ) {
    LazyColumn(
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (BuildConfig.DEBUG) {
        item("owner_bar") {
          OwnerDebugBar(
            onUnlockAll = { vm.debugUnlockAll() },
            onLockAll = { vm.debugResetRedemptions() },
          )
        }
      }
      item("header") { PartiesHeader() }
      item("join_cta") {
        Surface(
          color = Paper2,
          modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Terracotta.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onJoinGame),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "JOIN A MYSTERY NIGHT",
                style = MaterialTheme.typography.labelMedium.copy(
                  color = Terracotta,
                  fontFamily = Mono,
                  fontSize = 10.sp,
                  letterSpacing = 2.8.sp,
                  fontWeight = FontWeight.SemiBold,
                ),
              )
              Spacer(Modifier.height(4.dp))
              Text(
                "Got a four-letter code from your host?",
                style = MaterialTheme.typography.bodyMedium.copy(
                  color = InkSoft,
                  fontFamily = SerifBody,
                  fontStyle = FontStyle.Italic,
                  fontSize = 14.sp,
                ),
              )
            }
            Text(
              "→",
              style = MaterialTheme.typography.titleLarge.copy(
                color = Terracotta,
                fontFamily = Mono,
                fontSize = 24.sp,
              ),
            )
          }
        }
      }

      // PARTY MENUS — accordion, default expanded, brass accent.
      val partyOpen = expanded[SECTION_PARTY] == true
      item("section_party_header") {
        SectionAccordionHeader(
          label = "Party Menus",
          eyebrow = "READY NOW · 5 MENUS",
          count = PARTY_MENUS.size,
          isOpen = partyOpen,
          isStretch = false,
          isNew = false,
          accentColor = BrassBright,
          onToggle = { expanded[SECTION_PARTY] = !partyOpen },
        )
      }
      items(PARTY_MENUS, key = { it.id }) { menu ->
        val unlocked = isPro || menu.id in redeemed || PartiesViewModel.ALL_WILDCARD in redeemed
        AnimatedVisibility(
          visible = partyOpen,
          enter = fadeIn() + expandVertically(),
          exit = fadeOut() + shrinkVertically(),
        ) {
          MenuGridCard(
            menu = menu,
            unlocked = unlocked,
            onClick = { onOpenMenu(menu.id) },
          )
        }
      }

      // MYSTERY NIGHTS — accordion, default expanded, terracotta accent + NEW badge.
      val mysteryOpen = expanded[SECTION_MYSTERY] == true
      item("section_mystery_header") {
        SectionAccordionHeader(
          label = "Mystery Nights",
          eyebrow = "READY NOW · 5 MENUS · ROLE-PLAY",
          count = MYSTERY_MENUS.size,
          isOpen = mysteryOpen,
          isStretch = false,
          isNew = true,
          accentColor = Terracotta,
          onToggle = { expanded[SECTION_MYSTERY] = !mysteryOpen },
        )
      }
      items(MYSTERY_MENUS, key = { it.id }) { menu ->
        val unlocked = isPro || menu.id in redeemed || PartiesViewModel.ALL_WILDCARD in redeemed
        AnimatedVisibility(
          visible = mysteryOpen,
          enter = fadeIn() + expandVertically(),
          exit = fadeOut() + shrinkVertically(),
        ) {
          MenuGridCard(
            menu = menu,
            unlocked = unlocked,
            onClick = { onOpenMenu(menu.id) },
          )
        }
      }

      // SIXTY-FIVE TO FOLLOW — five accordion sections, default collapsed.
      item("coming_header") {
        ComingSoonHeader()
      }
      PLACEHOLDERS_BY_COHORT.forEach { (cohort, list) ->
        val isOpen = expanded[cohort.name] == true
        val cohortAccent = if (cohort == Cohort.MYSTERY_STRETCH) Terracotta else BrassBright
        item("cohort_${cohort.name}") {
          SectionAccordionHeader(
            label = cohort.label,
            eyebrow = if (cohort.isStretch) "STRETCH GOAL · ${list.size} MENUS" else "COMING · ${list.size} MENUS",
            count = list.size,
            isOpen = isOpen,
            isStretch = cohort.isStretch,
            isNew = false,
            accentColor = cohortAccent,
            onToggle = { expanded[cohort.name] = !isOpen },
          )
        }
        items(list, key = { "ph_${it.title}" }) { ph ->
          AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
          ) {
            PlaceholderCard(placeholder = ph)
          }
        }
      }
      item("footer") { PartiesFooter() }
    }
  }
}

private const val SECTION_PARTY = "section_party_ready"
private const val SECTION_MYSTERY = "section_mystery_ready"

/**
 * Unified accordion header used for all sections (Party / Mystery / cohorts).
 * Tight vertical rhythm so collapsed sections stack densely.
 */
@Composable
private fun SectionAccordionHeader(
  label: String,
  eyebrow: String,
  count: Int,
  isOpen: Boolean,
  isStretch: Boolean,
  isNew: Boolean,
  accentColor: androidx.compose.ui.graphics.Color,
  onToggle: () -> Unit,
) {
  // Two-part header: a 4dp colored accent strip identifies the section's type
  // (brass = party, terracotta = mystery), followed by the tappable header row.
  Column(modifier = Modifier.fillMaxWidth()) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .background(accentColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
    )
    Surface(
      color = Paper2,
      modifier = Modifier
        .fillMaxWidth()
        .border(0.5.dp, if (isOpen) accentColor.copy(alpha = 0.5f) else Rule, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
        .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
        .clickable(onClick = onToggle),
    ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall.copy(
              color = accentColor,
              fontFamily = Mono,
              fontSize = 9.sp,
              letterSpacing = 2.4.sp,
              fontWeight = FontWeight.SemiBold,
            ),
          )
          if (isStretch) {
            Spacer(Modifier.width(6.dp))
            TinyBadge(text = "STRETCH", textColor = BrassBright, bg = BrassDeep.copy(alpha = 0.6f))
          }
          if (isNew) {
            Spacer(Modifier.width(6.dp))
            TinyBadge(text = "NEW", textColor = Paper, bg = accentColor)
          }
        }
        Spacer(Modifier.height(3.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.titleMedium.copy(
            color = Ink,
            fontFamily = SerifDisplay,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontSize = 19.sp,
            lineHeight = 22.sp,
          ),
        )
      }
      Icon(
        imageVector = if (isOpen) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
        contentDescription = if (isOpen) "Collapse" else "Expand",
        tint = accentColor,
        modifier = Modifier.size(26.dp),
      )
    }
    }
  }
}

@Composable
private fun TinyBadge(text: String, textColor: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
  Box(
    modifier = Modifier
      .background(bg, RoundedCornerShape(2.dp))
      .padding(horizontal = 6.dp, vertical = 1.dp),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(
        color = textColor,
        fontFamily = Mono,
        fontSize = 8.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.SemiBold,
      ),
    )
  }
}


@Composable
private fun OwnerDebugBar(
  onUnlockAll: () -> Unit,
  onLockAll: () -> Unit,
) {
  Surface(
    color = Paper3,
    modifier = Modifier
      .fillMaxWidth()
      .border(0.5.dp, BrassBright.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "OWNER",
        style = MaterialTheme.typography.labelSmall.copy(
          color = BrassBright,
          fontFamily = Mono,
          fontSize = 9.sp,
          letterSpacing = 2.4.sp,
          fontWeight = FontWeight.SemiBold,
        ),
      )
      Spacer(Modifier.width(12.dp))
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(2.dp))
          .background(BrassBright)
          .clickable(onClick = onUnlockAll)
          .padding(horizontal = 12.dp, vertical = 6.dp),
      ) {
        Text(
          "UNLOCK ALL",
          style = MaterialTheme.typography.labelSmall.copy(
            color = Paper,
            fontFamily = Mono,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.SemiBold,
          ),
        )
      }
      Spacer(Modifier.width(8.dp))
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(2.dp))
          .border(0.5.dp, Rule, RoundedCornerShape(2.dp))
          .clickable(onClick = onLockAll)
          .padding(horizontal = 12.dp, vertical = 6.dp),
      ) {
        Text(
          "RESET",
          style = MaterialTheme.typography.labelSmall.copy(
            color = InkSoft,
            fontFamily = Mono,
            fontSize = 10.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.SemiBold,
          ),
        )
      }
      Spacer(Modifier.weight(1f))
      Text(
        text = "DEBUG",
        style = MaterialTheme.typography.labelSmall.copy(
          color = InkFaint,
          fontFamily = Mono,
          fontSize = 8.sp,
          letterSpacing = 2.0.sp,
        ),
      )
    }
  }
}

@Composable
private fun ComingSoonHeader() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 12.dp, bottom = 4.dp),
  ) {
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))
    Text(
      text = "FORTY-FIVE TO FOLLOW",
      style = MaterialTheme.typography.labelMedium.copy(
        color = BrassBright,
        fontFamily = Mono,
        letterSpacing = 3.0.sp,
      ),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "Ten per month for five months",
      style = MaterialTheme.typography.headlineSmall.copy(
        color = Ink,
        fontFamily = SerifDisplay,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
      ),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      text = "The remaining forty-five menus arrive on a published schedule, organized into four cohorts. Hotel Bars first, then Holidays, then Occasions, then the Speakeasy World Tour if the $25,000 stretch unlocks.",
      style = MaterialTheme.typography.bodyMedium.copy(
        color = InkSoft,
        fontFamily = SerifBody,
        lineHeight = 22.sp,
      ),
    )
  }
}

@Composable
private fun CohortDivider(label: String, count: Int) {
  Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
          color = BrassBright,
          fontFamily = Mono,
          fontSize = 10.sp,
          letterSpacing = 2.8.sp,
          fontWeight = FontWeight.SemiBold,
        ),
      )
      Spacer(Modifier.width(10.dp))
      Text(
        text = "· $count menus",
        style = MaterialTheme.typography.labelSmall.copy(
          color = InkFaint,
          fontFamily = Mono,
          fontSize = 10.sp,
          letterSpacing = 1.6.sp,
        ),
      )
    }
    Spacer(Modifier.height(8.dp))
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun PlaceholderCard(placeholder: PartyMenuPlaceholder) {
  Surface(
    color = Paper2,
    modifier = Modifier
      .fillMaxWidth()
      .border(0.5.dp, Rule, RoundedCornerShape(3.dp))
      .clip(RoundedCornerShape(3.dp)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${placeholder.eraCity.uppercase()} · ${placeholder.eraYear}",
          style = MaterialTheme.typography.labelSmall.copy(
            color = InkFaint,
            fontFamily = Mono,
            fontSize = 9.sp,
            letterSpacing = 2.0.sp,
          ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = placeholder.title,
          style = MaterialTheme.typography.titleMedium.copy(
            color = Ink,
            fontFamily = SerifDisplay,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            lineHeight = 22.sp,
          ),
        )
      }
      Spacer(Modifier.width(12.dp))
      Box(
        modifier = Modifier
          .background(BrassDeep.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
          .border(0.5.dp, BrassBright.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp),
      ) {
        Text(
          text = placeholder.shipBadge,
          style = MaterialTheme.typography.labelSmall.copy(
            color = BrassBright,
            fontFamily = Mono,
            fontSize = 9.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.SemiBold,
          ),
        )
      }
    }
  }
}

@Composable
private fun PartiesHeader() {
  Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
    Text(
      text = "CURATE A PARTY",
      style = MaterialTheme.typography.labelMedium.copy(color = BrassBright),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "Fifty menus from the manuscripts",
      style = MaterialTheme.typography.displaySmall.copy(
        color = Ink,
        fontFamily = SerifDisplay,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 36.sp,
      ),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      text = "Each menu names the bar, the bartender, the year. Five drinks, six small plates, a host timeline, a shopping list. You shop. You host.",
      style = MaterialTheme.typography.bodyMedium.copy(
        color = InkSoft,
        fontFamily = SerifBody,
        lineHeight = 22.sp,
      ),
    )
    Spacer(Modifier.height(16.dp))
    // Dotted-gold rule under the header.
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun MenuGridCard(
  menu: PartyMenu,
  unlocked: Boolean,
  onClick: () -> Unit,
) {
  // Color-coded by type. Brass = regular party menu. Terracotta = mystery night.
  // Strong visual differentiation at a single glance.
  val accent = if (menu.isMystery) Terracotta else BrassBright
  val accentDeep = if (menu.isMystery) TerracottaDeep else BrassDeep
  val typeLabel = if (menu.isMystery) "MYSTERY NIGHT · ROLE-PLAY" else "PARTY MENU"
  val lockChipLabel = if (menu.isMystery) "MYSTERY · KICKSTARTER" else "LOCKED · KICKSTARTER"

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(4.dp))
      .clickable(onClick = onClick),
  ) {
    // 4dp colored accent strip across the top — at-a-glance type identifier.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .background(accent),
    )
    Surface(
      color = Paper2,
      modifier = Modifier
        .fillMaxWidth()
        .border(width = 0.5.dp, color = Rule, shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)),
    ) {
      Column {
        // Hero panel — photographic image if the drawable is present, otherwise
        // a typographic title card on a paper-noise background.
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        ) {
          MenuHero(
            menuId = menu.id,
            modifier = Modifier.fillMaxSize(),
            fallbackTitle = menu.title,
            fallbackYear = menu.eraYear,
          )
          // Lock chip / unlocked chip (top-right) — accent-tinted by type.
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(10.dp)
              .background(
                color = if (unlocked) accentDeep else Paper3.copy(alpha = 0.88f),
                shape = RoundedCornerShape(2.dp),
              )
              .border(
                0.5.dp,
                accent.copy(alpha = if (unlocked) 0f else 0.7f),
                RoundedCornerShape(2.dp)
              )
              .padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Text(
              text = if (unlocked) "OPEN" else lockChipLabel,
              style = MaterialTheme.typography.labelSmall.copy(
                color = if (unlocked) accent else accent,
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 2.0.sp,
              ),
            )
          }
        }
        // Type-prefix eyebrow + era + title + source line.
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
          Text(
            text = typeLabel,
            style = MaterialTheme.typography.labelSmall.copy(
              color = accent,
              fontFamily = Mono,
              fontSize = 10.sp,
              letterSpacing = 3.2.sp,
              fontWeight = FontWeight.SemiBold,
            ),
          )
          Spacer(Modifier.height(5.dp))
          Text(
            text = "${menu.eraCity.uppercase()} · ${menu.eraYear} · ${menu.guestCount} GUESTS",
            style = MaterialTheme.typography.labelSmall.copy(
              color = InkFaint,
              fontFamily = Mono,
              fontSize = 9.sp,
              letterSpacing = 2.4.sp,
            ),
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = menu.title,
            style = MaterialTheme.typography.titleLarge.copy(
              color = Ink,
              fontFamily = SerifDisplay,
              fontStyle = FontStyle.Italic,
              fontWeight = FontWeight.Medium,
              fontSize = 22.sp,
              lineHeight = 28.sp,
            ),
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = menu.eraSourceLine,
            style = MaterialTheme.typography.bodySmall.copy(
              color = InkSoft,
              fontFamily = SerifBody,
              fontSize = 13.sp,
            ),
          )
        }
      }
    }
  }
}

@Composable
private fun PartiesFooter() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    DottedGoldRule(modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(18.dp))
    Text(
      text = "FIVE OF FIFTY · FORTY-FIVE TO FOLLOW",
      style = MaterialTheme.typography.labelMedium.copy(
        color = BrassBright,
        fontFamily = Mono,
        fontSize = 11.sp,
        letterSpacing = 3.0.sp,
      ),
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Backed the Kickstarter? Enter your code on any locked menu to unlock the set.",
      style = MaterialTheme.typography.bodySmall.copy(
        color = InkFaint,
        fontFamily = SerifBody,
        fontStyle = FontStyle.Italic,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      ),
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 32.dp),
    )
  }
}

/** 1px gold dotted rule. 8px dashes, 4px gaps, at 30% opacity to read as period. */
@Composable
fun DottedGoldRule(modifier: Modifier = Modifier) {
  val density = LocalDensity.current
  androidx.compose.foundation.Canvas(
    modifier = modifier.height(1.dp),
  ) {
    val dashPx = with(density) { 8.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val w = size.width
    var x = 0f
    while (x < w) {
      drawRect(
        color = BrassBright.copy(alpha = 0.32f),
        topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
        size = androidx.compose.ui.geometry.Size(dashPx.coerceAtMost(w - x), size.height),
      )
      x += dashPx + gapPx
    }
  }
}

/**
 * Hero image renderer with runtime drawable lookup. If the drawable resource
 * exists, render it cropped. If not (image-gen race condition), render a
 * typographic title card fallback on a paper texture. Never an SVG cocktail
 * glass standing in for the real thing — that violates the build spec
 * acceptance bar.
 */
@Composable
fun MenuHero(
  menuId: String,
  modifier: Modifier = Modifier,
  fallbackTitle: String,
  fallbackYear: Int,
) {
  val context = LocalContext.current
  val resId = remember(menuId) {
    context.resources.getIdentifier("menu_${menuId}_hero", "drawable", context.packageName)
  }
  if (resId != 0) {
    Image(
      painter = painterResource(resId),
      contentDescription = fallbackTitle,
      modifier = modifier,
      contentScale = ContentScale.Crop,
    )
  } else {
    TypographicTitleCard(
      title = fallbackTitle,
      year = fallbackYear,
      modifier = modifier,
    )
  }
}

/**
 * Fallback for missing hero images. Layered warm-paper gradient with a small
 * embossed wordmark and the menu's year in mono. No glassware glyphs, no
 * "stand-in" icons. The card itself is the typography.
 */
@Composable
private fun TypographicTitleCard(
  title: String,
  year: Int,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.background(
      brush = Brush.verticalGradient(
        colors = listOf(
          Paper3,
          Paper2,
        ),
      ),
    ),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(horizontal = 24.dp),
    ) {
      Text(
        text = "SPEAKEATER",
        style = MaterialTheme.typography.labelMedium.copy(
          color = BrassBright,
          fontFamily = Mono,
          fontSize = 10.sp,
          letterSpacing = 4.0.sp,
        ),
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
          color = Ink,
          fontFamily = SerifDisplay,
          fontStyle = FontStyle.Italic,
          fontWeight = FontWeight.Medium,
          fontSize = 22.sp,
          lineHeight = 26.sp,
        ),
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(10.dp))
      Text(
        text = "ANNO $year",
        style = MaterialTheme.typography.labelSmall.copy(
          color = InkFaint,
          fontFamily = Mono,
          fontSize = 10.sp,
          letterSpacing = 3.2.sp,
        ),
      )
    }
  }
}
