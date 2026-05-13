package app.pantrie.feature.parties.game

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.feature.notifications.NotificationPermissionPrompt
import app.pantrie.ui.theme.BrassBright
import app.pantrie.ui.theme.BrassDeep
import app.pantrie.ui.theme.Ink
import app.pantrie.ui.theme.InkFaint
import app.pantrie.ui.theme.InkSoft
import app.pantrie.ui.theme.Mono
import app.pantrie.ui.theme.Paper
import app.pantrie.ui.theme.Paper2
import app.pantrie.ui.theme.Paper3
import app.pantrie.ui.theme.Rule
import app.pantrie.ui.theme.SerifBody
import app.pantrie.ui.theme.SerifDisplay
import app.pantrie.ui.theme.Terracotta

/**
 * Five game-flow screens condensed into a single file. UI is functional and
 * brand-coded but intentionally minimal — Kyle iterates on UX easily, the
 * hard work is the WebSocket + script engine + writing.
 *
 * IMPORTANT: every screen uses the SAME GameViewModel instance, scoped to
 * the host Activity, so state (role, code, character, current beat) persists
 * across the host-lobby → in-game and join → character → in-game
 * transitions. The default Compose-Nav hiltViewModel() scopes per
 * NavBackStackEntry, which would give each screen its own VM and lose the
 * lobby state on Begin — see the "Waiting for the host to assign your
 * role" bug from 2026-05-12.
 */
@Composable
private fun activityGameVm(): GameViewModel {
  val activity = LocalContext.current as ComponentActivity
  return hiltViewModel(activity)
}

// ===========================================================================
//  HOST LOBBY — host sees code + players joining, taps Begin
// ===========================================================================
@Composable
fun HostLobbyScreen(
  menuId: String,
  hostName: String,
  userId: String,
  onBegin: () -> Unit,
  onClose: () -> Unit,
  vm: GameViewModel = activityGameVm(),
) {
  val state by vm.state.collectAsState()

  // Mystery Nights needs POST_NOTIFICATIONS so phones can vibrate on every
  // beat per doctrine. Trigger the system prompt the first time a host
  // opens a game lobby.
  NotificationPermissionPrompt()

  androidx.compose.runtime.LaunchedEffect(menuId) {
    if (state.code.isEmpty()) {
      val last = vm.lastSession()
      if (last != null && last.role == "host" && last.menuId == menuId) {
        // Host's app was killed mid-game. Reconnect to the existing room
        // instead of minting a new code. The DO re-broadcasts the lobby.
        vm.resumeHosting(last, userId)
      } else {
        vm.startHosting(menuId, hostName, userId)
      }
    }
  }
  // Auto-transition into the game view the moment the host taps Begin and
  // the server flips the phase. No second tap needed.
  androidx.compose.runtime.LaunchedEffect(state.phase) {
    if (state.phase == "playing" || state.phase == "reveal") onBegin()
  }

  GameSurface {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
      EyebrowLine(text = "HOSTING · MYSTERY NIGHT", color = Terracotta)
      Spacer(Modifier.height(8.dp))
      Text(
        text = vm.menuTitle.takeIf { it.isNotBlank() } ?: "Mystery Night",
        style = MaterialTheme.typography.headlineMedium.copy(
          color = Ink, fontFamily = SerifDisplay, fontStyle = FontStyle.Italic,
          fontSize = 28.sp,
        ),
      )
      Spacer(Modifier.height(28.dp))
      // Big 4-letter code
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Paper2, RoundedCornerShape(6.dp))
          .border(1.dp, Terracotta.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
          .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            "FOUR-LETTER CODE",
            style = MaterialTheme.typography.labelMedium.copy(
              color = BrassBright, fontFamily = Mono, letterSpacing = 3.0.sp, fontSize = 10.sp,
            ),
          )
          Spacer(Modifier.height(10.dp))
          Text(
            text = state.code.ifBlank { "----" },
            style = MaterialTheme.typography.displayLarge.copy(
              color = Ink, fontFamily = SerifDisplay,
              fontWeight = FontWeight.Medium, fontSize = 72.sp,
              letterSpacing = 8.sp,
            ),
          )
          Spacer(Modifier.height(8.dp))
          Text(
            "Ask each guest to open Speakeater · Join a Game · type this code",
            style = MaterialTheme.typography.bodySmall.copy(
              color = InkFaint, fontFamily = SerifBody, fontStyle = FontStyle.Italic, fontSize = 13.sp,
            ),
            textAlign = TextAlign.Center,
          )
        }
      }
      Spacer(Modifier.height(28.dp))
      Text(
        "PLAYERS JOINED · ${state.players.size}",
        style = MaterialTheme.typography.labelMedium.copy(
          color = BrassBright, fontFamily = Mono, letterSpacing = 2.4.sp, fontSize = 10.sp,
        ),
      )
      Spacer(Modifier.height(8.dp))
      if (state.players.isEmpty()) {
        Text(
          "Waiting for the first guest…",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = InkFaint, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
          ),
        )
      } else {
        state.players.forEach { p ->
          Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(BrassBright, RoundedCornerShape(4.dp)))
            Spacer(Modifier.size(10.dp))
            Text(
              p.name,
              style = MaterialTheme.typography.bodyLarge.copy(
                color = Ink, fontFamily = SerifBody, fontSize = 17.sp,
              ),
            )
            if (p.character_id != null) {
              Spacer(Modifier.size(10.dp))
              Text(
                "· " + (findCastName(state.menuId, p.character_id) ?: p.character_id),
                style = MaterialTheme.typography.bodySmall.copy(
                  color = InkSoft, fontFamily = SerifBody, fontStyle = FontStyle.Italic, fontSize = 13.sp,
                ),
              )
            }
          }
        }
      }
      Spacer(Modifier.weight(1f))
      Button(
        onClick = { vm.hostBegin() },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.players.size >= 1 && state.phase == "lobby",
        colors = ButtonDefaults.buttonColors(
          containerColor = Terracotta, contentColor = Paper,
        ),
        shape = RoundedCornerShape(3.dp),
      ) {
        Text(
          "BEGIN THE NIGHT",
          style = MaterialTheme.typography.labelLarge.copy(
            fontFamily = Mono, letterSpacing = 2.8.sp,
          ),
          modifier = Modifier.padding(vertical = 4.dp),
        )
      }
      if (state.phase == "playing" || state.phase == "reveal") {
        Spacer(Modifier.height(8.dp))
        Text(
          "Game in progress — open the in-game view",
          style = MaterialTheme.typography.bodySmall.copy(
            color = BrassBright, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
          ),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
        )
      }
      state.errorMessage?.let { e ->
        Spacer(Modifier.height(8.dp))
        Text(
          e,
          color = Terracotta,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SerifBody, fontStyle = FontStyle.Italic),
        )
      }
    }
  }
}

// ===========================================================================
//  PLAYER JOIN — code entry
// ===========================================================================
@Composable
fun PlayerJoinScreen(
  onJoined: () -> Unit,
  onClose: () -> Unit,
  vm: GameViewModel = activityGameVm(),
) {
  val state by vm.state.collectAsState()
  // Prefill from the last session so a player who crashed mid-game (or
  // backed out and is rejoining) gets back into the same room+character
  // with one tap.
  val last = remember { vm.lastSession() }
  var code by remember { mutableStateOf(last?.code.orEmpty()) }
  var name by remember { mutableStateOf(last?.name.orEmpty()) }
  val isRejoin = last != null && last.role == "player"

  // POST_NOTIFICATIONS prompt — players need vibrations on every beat.
  NotificationPermissionPrompt()

  androidx.compose.runtime.LaunchedEffect(state.myCharacter) {
    if (state.myCharacter != null) onJoined()
  }

  GameSurface {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
      EyebrowLine(
        text = if (isRejoin) "REJOIN YOUR MYSTERY NIGHT" else "JOIN A MYSTERY NIGHT",
        color = Terracotta,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        if (isRejoin) "Your last room is filled in. Tap to reconnect."
        else "Type the host's four-letter code",
        style = MaterialTheme.typography.headlineMedium.copy(
          color = Ink, fontFamily = SerifDisplay, fontStyle = FontStyle.Italic, fontSize = 26.sp,
        ),
      )
      Spacer(Modifier.height(32.dp))
      OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(24) },
        placeholder = { Text("Your name", style = MaterialTheme.typography.bodyMedium.copy(color = InkFaint, fontFamily = SerifBody)) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = Terracotta, unfocusedBorderColor = Rule,
          focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Terracotta,
          focusedContainerColor = Paper3, unfocusedContainerColor = Paper3,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(14.dp))
      OutlinedTextField(
        value = code,
        onValueChange = { raw -> code = raw.uppercase().filter { it.isLetterOrDigit() }.take(4) },
        placeholder = { Text("CODE", style = MaterialTheme.typography.bodyLarge.copy(color = InkFaint, fontFamily = Mono, fontSize = 32.sp, letterSpacing = 8.sp)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = Terracotta, unfocusedBorderColor = Rule,
          focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Terracotta,
          focusedContainerColor = Paper3, unfocusedContainerColor = Paper3,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(20.dp))
      Button(
        onClick = { if (code.length == 4 && name.isNotBlank()) vm.joinAsPlayer(code, name) },
        enabled = code.length == 4 && name.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Paper),
      ) {
        Text(
          if (isRejoin) "REJOIN" else "JOIN",
          style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono, letterSpacing = 2.8.sp),
          modifier = Modifier.padding(vertical = 4.dp),
        )
      }
      if (state.connection == GameConnectionState.CONNECTING || state.connection == GameConnectionState.CONNECTED) {
        Spacer(Modifier.height(14.dp))
        Text(
          if (state.myCharacter == null) "Connecting to room ${state.code}…" else "Connected. Loading your character…",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = BrassBright, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
          ),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      state.errorMessage?.let { e ->
        Spacer(Modifier.height(8.dp))
        Text(e, color = Terracotta, style = MaterialTheme.typography.bodySmall.copy(fontFamily = SerifBody, fontStyle = FontStyle.Italic))
      }
    }
  }
}

// ===========================================================================
//  CHARACTER CARD — the ONE big read per doctrine. Player joins, lands here,
//  studies their character (name, role, agenda, secret) for 2-3 minutes.
//  Auto-transitions to InGameScreen the moment the host taps Begin.
// ===========================================================================
@Composable
fun CharacterCardScreen(
  onBegin: () -> Unit,
  onClose: () -> Unit,
  vm: GameViewModel = activityGameVm(),
) {
  val state by vm.state.collectAsState()
  val character = state.myCharacter

  androidx.compose.runtime.LaunchedEffect(state.phase) {
    if (state.phase == "playing" || state.phase == "reveal") onBegin()
  }

  GameSurface {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
    ) {
      EyebrowLine("YOUR CHARACTER", Terracotta)
      Spacer(Modifier.height(8.dp))
      if (character == null) {
        Text(
          "Waiting for the host to assign your role…",
          style = MaterialTheme.typography.bodyLarge.copy(
            color = InkFaint, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
          ),
        )
      } else {
        Text(
          character.name,
          style = MaterialTheme.typography.displayMedium.copy(
            color = Ink, fontFamily = SerifDisplay, fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 46.sp,
          ),
        )
        Spacer(Modifier.height(16.dp))
        EyebrowLine("ROLE", BrassBright)
        Spacer(Modifier.height(6.dp))
        Text(
          character.role,
          style = MaterialTheme.typography.bodyLarge.copy(
            color = Ink, fontFamily = SerifBody, fontSize = 17.sp, lineHeight = 26.sp,
          ),
        )
        if (character.objective.isNotBlank()) {
          Spacer(Modifier.height(20.dp))
          EyebrowLine("YOUR AGENDA FOR THE NIGHT", BrassBright)
          Spacer(Modifier.height(6.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(Paper2, RoundedCornerShape(3.dp))
              .border(0.5.dp, BrassDeep.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
              .padding(14.dp),
          ) {
            Text(
              character.objective,
              style = MaterialTheme.typography.bodyMedium.copy(
                color = Ink, fontFamily = SerifBody,
                fontSize = 15.sp, lineHeight = 22.sp,
              ),
            )
          }
        }
        Spacer(Modifier.height(20.dp))
        EyebrowLine("YOUR SECRET · DO NOT SHARE", Terracotta)
        Spacer(Modifier.height(6.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(Paper3, RoundedCornerShape(3.dp))
            .border(0.5.dp, Terracotta.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(14.dp),
        ) {
          Text(
            character.secret,
            style = MaterialTheme.typography.bodyMedium.copy(
              color = Ink, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
              fontSize = 15.sp, lineHeight = 22.sp,
            ),
          )
        }
      }
      if (character != null) {
        Spacer(Modifier.height(20.dp))
        EyebrowLine("HOW THE NIGHT RUNS", BrassBright)
        Spacer(Modifier.height(6.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(Paper3, RoundedCornerShape(3.dp))
            .border(0.5.dp, Rule, RoundedCornerShape(3.dp))
            .padding(14.dp),
        ) {
          Text(
            "Study your character now. This is the long read. Once the host taps BEGIN your phone will buzz when the story whispers to you. Each whisper is a sentence — read it, decide, put the phone face-down. The night runs across the dinner table, not the screen.",
            style = MaterialTheme.typography.bodySmall.copy(
              color = Ink, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
              fontSize = 13.sp, lineHeight = 19.sp,
            ),
          )
        }
      }
      Spacer(Modifier.height(20.dp))
      Text(
        "ROOM · ${state.code}",
        style = MaterialTheme.typography.labelMedium.copy(
          color = InkFaint, fontFamily = Mono, letterSpacing = 2.8.sp,
        ),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "Waiting for the host to begin the night.",
        style = MaterialTheme.typography.bodySmall.copy(
          color = InkFaint, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
        ),
      )
    }
  }
}

// ===========================================================================
//  IN-GAME — persistent character header + ephemeral beat takeover
//
//  Per Mystery Nights design doctrine (memory/project_mystery_nights_design_doctrine.md):
//    • Character card collapses to a compact persistent header at the top.
//    • Body shows ONLY the current beat. Beats are ephemeral (auto-dismissed
//      by the ViewModel after 8 seconds or on player action) so the phone
//      goes back face-down on the table.
//    • Cover beats render with quiet narrative chrome so the label never
//      reveals to a player that they got a cover instead of a real twist.
// ===========================================================================
@Composable
fun InGameScreen(
  onReveal: () -> Unit,
  onClose: () -> Unit,
  vm: GameViewModel = activityGameVm(),
) {
  val state by vm.state.collectAsState()

  androidx.compose.runtime.LaunchedEffect(state.phase) {
    if (state.phase == "reveal") onReveal()
  }

  GameSurface {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        val beat = state.currentBeat
        val tLabel = if (beat != null) "T+${beat.time_offset_minutes} · BEAT ${state.currentBeatIndex + 1}" else "AT THE TABLE"
        Text(
          tLabel,
          style = MaterialTheme.typography.labelMedium.copy(
            color = Terracotta, fontFamily = Mono, letterSpacing = 3.0.sp,
          ),
        )
        Spacer(Modifier.weight(1f))
        Text(
          "ROOM ${state.code}",
          style = MaterialTheme.typography.labelSmall.copy(
            color = InkFaint, fontFamily = Mono, letterSpacing = 2.2.sp,
          ),
        )
      }
      Spacer(Modifier.height(12.dp))

      // Persistent character header — always visible on player phones once
      // a character has been assigned. Stays put through beat changes.
      val character = state.myCharacter
      if (state.role == "player" && character != null) {
        PersistentCharacterHeader(character)
        Spacer(Modifier.height(18.dp))
      } else if (state.role == "host") {
        PersistentHostPlaybook(playersCount = state.players.size, phase = state.phase)
        Spacer(Modifier.height(18.dp))
      }

      // Beat body — ephemeral. When no beat is active, show a quiet
      // atmospheric placeholder so the player puts the phone down.
      val beat = state.currentBeat
      if (beat == null) {
        BetweenBeatsPlaceholder(state.role, state.phase, state.myCharacter != null)
      } else {
        // Director view: as host, a TWIST targeted at a specific character
        // is something the host OBSERVES, not acts on. Label it clearly so
        // the host knows they are watching, not playing.
        val directorTargetName = if (state.role == "host" && beat.target != "all" && beat.kind != "COVER") {
          scriptFor(state.menuId)?.cast?.firstOrNull { it.id == beat.target }?.name
        } else null
        BeatBody(
          beat = beat,
          pendingActions = state.pendingAction?.actions,
          onAction = { vm.submitAction(it) },
          directorTargetName = directorTargetName,
          isHost = state.role == "host",
        )
      }

      // Host director controls
      if (state.role == "host") {
        Spacer(Modifier.height(24.dp))
        EyebrowLine("HOST · DIRECTOR VIEW", BrassBright)
        Spacer(Modifier.height(6.dp))
        Row {
          Button(
            onClick = { vm.hostAdvance() },
            colors = ButtonDefaults.buttonColors(containerColor = BrassBright, contentColor = Paper),
            shape = RoundedCornerShape(3.dp),
            modifier = Modifier.weight(1f),
          ) { Text("ADVANCE", style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono, letterSpacing = 2.4.sp)) }
          Spacer(Modifier.size(8.dp))
          Button(
            onClick = { if (state.phase == "playing") vm.hostPause() else vm.hostResume() },
            colors = ButtonDefaults.buttonColors(containerColor = Paper3, contentColor = Ink),
            shape = RoundedCornerShape(3.dp),
            modifier = Modifier.weight(1f),
          ) { Text(if (state.phase == "paused") "RESUME" else "PAUSE", style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono, letterSpacing = 2.4.sp)) }
        }
      }
    }
  }
}

@Composable
private fun PersistentHostPlaybook(playersCount: Int, phase: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(Paper2, RoundedCornerShape(3.dp))
      .border(0.5.dp, BrassDeep.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp),
  ) {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          "Hosting the night",
          style = MaterialTheme.typography.titleLarge.copy(
            color = Ink, fontFamily = SerifDisplay, fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 22.sp,
          ),
          modifier = Modifier.weight(1f),
        )
        Text(
          "$playersCount AT TABLE",
          style = MaterialTheme.typography.labelSmall.copy(
            color = BrassBright, fontFamily = Mono, letterSpacing = 2.0.sp, fontSize = 9.sp,
          ),
        )
      }
      Spacer(Modifier.height(6.dp))
      Text(
        "YOUR JOB",
        style = MaterialTheme.typography.labelSmall.copy(
          color = BrassBright, fontFamily = Mono, letterSpacing = 2.4.sp, fontSize = 9.sp,
        ),
      )
      Spacer(Modifier.height(2.dp))
      Text(
        "Read NARRATIVE beats aloud to the table. TWIST beats are private — only one guest's phone holds the real one. Pour, eat, keep the room moving. The next beat fires on its timer; ADVANCE pushes it sooner.",
        style = MaterialTheme.typography.bodySmall.copy(
          color = Ink, fontFamily = SerifBody, fontSize = 13.sp, lineHeight = 18.sp,
        ),
      )
    }
  }
}

@Composable
private fun PersistentCharacterHeader(character: CharacterAssignment) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(Paper2, RoundedCornerShape(3.dp))
      .border(0.5.dp, BrassDeep.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
      .padding(horizontal = 14.dp, vertical = 12.dp),
  ) {
    Column {
      Text(
        character.name,
        style = MaterialTheme.typography.titleLarge.copy(
          color = Ink, fontFamily = SerifDisplay, fontStyle = FontStyle.Italic,
          fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 24.sp,
        ),
      )
      if (character.objective.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
          "AGENDA",
          style = MaterialTheme.typography.labelSmall.copy(
            color = BrassBright, fontFamily = Mono, letterSpacing = 2.4.sp, fontSize = 9.sp,
          ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
          character.objective,
          style = MaterialTheme.typography.bodySmall.copy(
            color = Ink, fontFamily = SerifBody, fontSize = 13.sp, lineHeight = 18.sp,
          ),
        )
      }
      Spacer(Modifier.height(6.dp))
      Text(
        "SECRET",
        style = MaterialTheme.typography.labelSmall.copy(
          color = Terracotta, fontFamily = Mono, letterSpacing = 2.4.sp, fontSize = 9.sp,
        ),
      )
      Spacer(Modifier.height(2.dp))
      Text(
        character.secret,
        style = MaterialTheme.typography.bodySmall.copy(
          color = Ink, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
          fontSize = 13.sp, lineHeight = 18.sp,
        ),
      )
    }
  }
}

@Composable
private fun BeatBody(
  beat: GameBeat,
  pendingActions: List<GameBeatAction>?,
  onAction: (String) -> Unit,
  directorTargetName: String? = null,
  isHost: Boolean = false,
) {
  // Cover beats render with the same chrome as a NARRATIVE beat so the
  // label never tips a player off that they got a cover instead of a real
  // twist. Movement-layer beats (STEP_AWAY / RECON / GATHER_INTEL / etc.
  // from audit Part 4) get their own chrome — the player is being asked
  // to physically leave the table, not read text. CLOCK is a world-clock
  // broadcast read aloud by the host. When the viewer is the host and
  // the beat is private to someone else, swap to a director-view label.
  val movementKind = beat.kind in setOf(
    "STEP_AWAY", "RECON", "GATHER_INTEL", "RENDEZVOUS", "SNEAK", "DEAD_DROP", "BE_SEEN", "MISSED",
  )
  val (eyebrowText, eyebrowColor, accentBorder) = when {
    directorTargetName != null -> Triple(
      "DIRECTOR · ${beat.kind} FIRING AT ${directorTargetName.uppercase()}",
      BrassDeep, BrassDeep.copy(alpha = 0.5f),
    )
    beat.kind == "CLOCK" -> Triple("THE CLOCK", BrassDeep, BrassDeep.copy(alpha = 0.7f))
    beat.kind == "TWIST" -> Triple("TWIST", Terracotta, Terracotta.copy(alpha = 0.5f))
    beat.kind == "SYSTEM" -> Triple("SYSTEM", BrassBright, Rule)
    beat.kind == "COVER" -> Triple("STORY", BrassBright, Rule)
    movementKind -> Triple(beat.kind.replace('_', ' '), Terracotta, Terracotta.copy(alpha = 0.6f))
    else -> Triple("NARRATIVE", BrassBright, Rule)
  }
  EyebrowLine(eyebrowText, eyebrowColor)
  Spacer(Modifier.height(6.dp))
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(Paper2, RoundedCornerShape(3.dp))
      .border(0.5.dp, accentBorder, RoundedCornerShape(3.dp))
      .padding(16.dp),
  ) {
    Text(
      beat.body,
      style = MaterialTheme.typography.bodyLarge.copy(
        color = Ink, fontFamily = SerifBody, fontSize = 17.sp, lineHeight = 26.sp,
      ),
    )
  }
  pendingActions?.let { actions ->
    Spacer(Modifier.height(16.dp))
    EyebrowLine("CHOOSE", BrassBright)
    Spacer(Modifier.height(6.dp))
    actions.forEach { action ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .background(Paper3, RoundedCornerShape(3.dp))
          .border(0.5.dp, BrassBright.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
          .clip(RoundedCornerShape(3.dp))
          .clickable { onAction(action.id) }
          .padding(14.dp),
      ) {
        Text(
          action.label,
          style = MaterialTheme.typography.bodyMedium.copy(
            color = Ink, fontFamily = SerifBody, fontSize = 15.sp,
          ),
        )
      }
    }
  }
  // Director-track stage direction — host phone only. Quietly italic.
  if (isHost && !beat.host_note.isNullOrBlank()) {
    Spacer(Modifier.height(12.dp))
    EyebrowLine("HOST · STAGE NOTE", BrassDeep)
    Spacer(Modifier.height(4.dp))
    Text(
      beat.host_note!!,
      style = MaterialTheme.typography.bodySmall.copy(
        color = InkSoft, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
        fontSize = 13.sp, lineHeight = 18.sp,
      ),
    )
  }
}

@Composable
private fun BetweenBeatsPlaceholder(role: String, phase: String, hasCharacter: Boolean) {
  val (line1, line2) = when {
    phase == "paused" ->
      "The host has paused the night." to "Stand by. The story is still here."
    role == "host" && phase == "lobby" ->
      "Lobby is open." to "Tap BEGIN THE NIGHT once everyone is in."
    role == "host" ->
      "Between beats." to "Eat. Pour. Keep the room moving. The next whisper fires on its timer, or tap ADVANCE."
    !hasCharacter ->
      "Waiting for the host to assign your role…" to "Your character card arrives any second."
    phase == "lobby" ->
      "Waiting for the host to begin the night." to "Study your character. Order what your character would drink."
    else ->
      "The story continues at the table." to "Your phone will buzz when the story finds you. Eat. Drink. Stay in character."
  }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 28.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        line1,
        style = MaterialTheme.typography.bodyLarge.copy(
          color = Ink, fontFamily = SerifDisplay, fontStyle = FontStyle.Italic,
          fontSize = 18.sp,
        ),
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        line2,
        style = MaterialTheme.typography.bodySmall.copy(
          color = InkFaint, fontFamily = SerifBody, fontStyle = FontStyle.Italic,
          fontSize = 13.sp,
        ),
        textAlign = TextAlign.Center,
      )
    }
  }
}

// ===========================================================================
//  REVEAL — endgame
// ===========================================================================
@Composable
fun RevealScreen(
  onClose: () -> Unit,
  vm: GameViewModel = activityGameVm(),
) {
  val state by vm.state.collectAsState()
  GameSurface {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
    ) {
      EyebrowLine("THE REVEAL", Terracotta)
      Spacer(Modifier.height(8.dp))
      val killerName = state.revealKillerId?.let { findCastName(state.menuId, it) } ?: "—"
      Text(
        "It was $killerName.",
        style = MaterialTheme.typography.displayMedium.copy(
          color = Ink, fontFamily = SerifDisplay, fontStyle = FontStyle.Italic, fontSize = 36.sp,
        ),
      )
      Spacer(Modifier.height(20.dp))
      Text(
        state.revealText.orEmpty(),
        style = MaterialTheme.typography.bodyLarge.copy(
          color = Ink, fontFamily = SerifBody, fontSize = 17.sp, lineHeight = 28.sp,
        ),
      )
      Spacer(Modifier.height(32.dp))
      Button(
        onClick = { vm.close(); onClose() },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Paper),
        shape = RoundedCornerShape(3.dp),
      ) {
        Text("CLOSE THE NIGHT", style = MaterialTheme.typography.labelLarge.copy(fontFamily = Mono, letterSpacing = 2.8.sp), modifier = Modifier.padding(vertical = 4.dp))
      }
    }
  }
}

// ===========================================================================
//  Shared scaffolding
// ===========================================================================
@Composable
private fun GameSurface(content: @Composable () -> Unit) {
  Surface(modifier = Modifier.fillMaxSize(), color = Paper) { content() }
}

@Composable
private fun EyebrowLine(text: String, color: androidx.compose.ui.graphics.Color) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium.copy(
      color = color, fontFamily = Mono, letterSpacing = 3.0.sp,
      fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
    ),
  )
}

/** Look up a character's display name from any mystery script's cast. */
private fun findCastName(menuId: String, characterId: String?): String? {
  if (characterId == null) return null
  return scriptFor(menuId)?.cast?.firstOrNull { it.id == characterId }?.name
}
