package app.pantrie.feature.parties.game

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.feature.parties.findMenu
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.CreateGameRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Aggregate game UI state — one source of truth for all 5 game screens. */
data class GameUiState(
  val connection: GameConnectionState = GameConnectionState.DISCONNECTED,
  val role: String = "",                                // "host" or "player"
  val code: String = "",                                 // 4-letter room code
  val menuId: String = "",
  val phase: String = "lobby",                          // lobby | playing | paused | reveal | done
  val players: List<LobbyPlayer> = emptyList(),
  val myCharacter: CharacterAssignment? = null,         // player only
  val currentBeat: GameBeat? = null,                    // last beat pushed to this client
  val currentBeatIndex: Int = -1,
  val pendingAction: GameBeat? = null,                  // a TWIST beat awaiting this player's choice
  val recordedActions: List<RecordedAction> = emptyList(),
  /**
   * HOST-ONLY rolling log of player choices on TWIST beats. Populated by
   * PlayerActionRecorded events. The host needs this to direct the room
   * intelligently — Blood on the Clocktower's Storyteller sees every vote;
   * Speakeater's host was flying blind before 2026-05-16. Kept to the last
   * 20 entries to bound memory.
   */
  val hostActionLog: List<HostPlayerActionLogEntry> = emptyList(),
  val revealText: String? = null,
  val revealKillerId: String? = null,
  val errorMessage: String? = null,
)

data class CharacterAssignment(
  val characterId: String,
  val name: String,
  val role: String,
  val secret: String,
  val objective: String = "",
  val bluffs: List<String> = emptyList(),
)

data class RecordedAction(val beatIndex: Int, val actionId: String)

/** A single entry in the host's player-action log — "who chose what". */
data class HostPlayerActionLogEntry(
  val beatIndex: Int,
  val actionId: String,
  val playerName: String,
  val characterId: String,
  val atEpochMs: Long,
)

@HiltViewModel
class GameViewModel @Inject constructor(
  private val socket: GameSocket,
  private val api: PantrieApi,
  private val notifications: GameNotifications,
  private val sessionStore: GameSessionStore,
  @ApplicationContext private val appContext: Context,
) : ViewModel() {

  private val _state = MutableStateFlow(GameUiState())
  val state: StateFlow<GameUiState> = _state.asStateFlow()

  /**
   * Doctrine: players' phones spend 5-10 seconds on a beat, then go back
   * face-down on the table. We auto-dismiss the current beat after a
   * body-length-aware delay (base 4s + ~35ms per character, floored at 6s,
   * capped at 18s) so longer cover beats stay visible long enough to read,
   * but short whispers don't linger. Cancelled when the player picks an
   * action, on close, or when the next beat arrives. Host phones do not
   * auto-dismiss — the host is directing.
   * Pre-2026-05-16 this was a fixed 8s window. Audit Fix #4.
   */
  private var beatDismissJob: Job? = null
  private val MIN_BEAT_VISIBLE_MS = 6_000L
  private val MAX_BEAT_VISIBLE_MS = 18_000L
  private val BEAT_BASE_MS = 4_000L
  private val BEAT_PER_CHAR_MS = 35L

  private fun computeDismissMs(body: String): Long {
    val raw = BEAT_BASE_MS + body.length * BEAT_PER_CHAR_MS
    return raw.coerceIn(MIN_BEAT_VISIBLE_MS, MAX_BEAT_VISIBLE_MS)
  }

  init {
    socket.state.onEach { connState ->
      _state.value = _state.value.copy(connection = connState)
    }.launchIn(viewModelScope)

    socket.incoming.onEach { msg -> handleIncoming(msg) }.launchIn(viewModelScope)
  }

  /** HOST: open the room via REST, then connect WebSocket and send the script. */
  fun startHosting(menuId: String, hostName: String, userId: String) {
    viewModelScope.launch {
      runCatching { api.createGame(CreateGameRequest(menuId = menuId)) }
        .onSuccess { resp ->
          _state.value = _state.value.copy(
            role = "host",
            code = resp.code,
            menuId = menuId,
          )
          sessionStore.save(ActiveGame(
            code = resp.code, name = hostName, role = "host", menuId = menuId,
          ))
          socket.connect(resp.code, role = "host", name = hostName, userId = userId)
          // After WS is open we'll send host_create — wait for the "connected" message
          // to signal the socket is alive, then send the script.
        }
        .onFailure { e ->
          _state.value = _state.value.copy(errorMessage = "Could not create room: ${e.message}")
        }
    }
  }

  /**
   * HOST resume: reconnect to an existing room without minting a new code.
   * The DO sees the existing room state, the script is already stored, and
   * the lobby state is re-broadcast. Used when the host's app was killed
   * mid-game.
   */
  fun resumeHosting(active: ActiveGame, userId: String = "") {
    _state.value = _state.value.copy(
      role = "host",
      code = active.code,
      menuId = active.menuId,
    )
    sessionStore.save(active)
    socket.connect(active.code, role = "host", name = active.name, userId = userId)
  }

  /** PLAYER: join an existing room with the 4-letter code. */
  private var pendingPlayerName: String = ""
  fun joinAsPlayer(code: String, name: String) {
    val cleaned = code.uppercase().trim()
    pendingPlayerName = name
    _state.value = _state.value.copy(role = "player", code = cleaned)
    sessionStore.save(ActiveGame(
      code = cleaned, name = name, role = "player", menuId = "",
    ))
    socket.connect(cleaned, role = "player", name = name)
  }

  /** Read the persisted active game (for prefilling the join screen or showing the resume callout). */
  fun lastSession(): ActiveGame? = sessionStore.load()

  fun hostBegin() = socket.sendHostBegin()
  fun hostAdvance() = socket.sendHostAdvance()
  fun hostPause() = socket.sendHostPause()
  fun hostResume() = socket.sendHostResume()

  fun submitAction(actionId: String) {
    val beatIndex = _state.value.currentBeatIndex
    if (beatIndex < 0) return
    socket.sendPlayerAction(beatIndex, actionId)
    beatDismissJob?.cancel()
    _state.value = _state.value.copy(
      pendingAction = null,
      currentBeat = null,
      recordedActions = _state.value.recordedActions + RecordedAction(beatIndex, actionId),
    )
  }

  /**
   * Fully end the session. Closes the WebSocket, stops the foreground
   * service, dismisses notifications, and clears the persisted rejoin
   * record. Called when the user taps "Close the Night" or otherwise
   * leaves the game intentionally.
   */
  fun close() {
    beatDismissJob?.cancel()
    stopSessionService()
    notifications.dismissBeat()
    sessionStore.clear()
    socket.close()
    _state.value = GameUiState()
  }

  /**
   * Drop the socket without clearing the persisted rejoin record. Used
   * when the user backs out of an in-progress game and may want to
   * rejoin shortly. Different from [close], which is a hard end.
   */
  fun pauseConnection() {
    beatDismissJob?.cancel()
    socket.close()
    _state.value = GameUiState()
  }

  private fun startSessionService() {
    val s = _state.value
    if (s.code.isBlank()) return
    GameSessionService.start(appContext, s.code, s.players.size)
  }

  private fun stopSessionService() {
    GameSessionService.stop(appContext)
  }

  private fun scheduleBeatDismiss() {
    // Only auto-dismiss on player phones. Host stays sticky for directing.
    if (_state.value.role != "player") return
    beatDismissJob?.cancel()
    val body = _state.value.currentBeat?.body.orEmpty()
    val ms = computeDismissMs(body)
    beatDismissJob = viewModelScope.launch {
      delay(ms)
      _state.value = _state.value.copy(currentBeat = null, pendingAction = null)
    }
  }

  fun clearError() {
    _state.value = _state.value.copy(errorMessage = null)
  }

  private fun handleIncoming(msg: IncomingMessage) {
    when (msg) {
      is IncomingMessage.Connected -> {
        // Once the socket is open, send the role-specific kickoff message.
        if (msg.role == "host") {
          val script = scriptFor(_state.value.menuId) ?: return
          socket.sendHostCreate(_state.value.menuId, script)
        } else if (msg.role == "player") {
          socket.sendPlayerJoin(_state.value.code, pendingPlayerName)
        }
      }
      is IncomingMessage.RoomCreated -> {
        _state.value = _state.value.copy(code = msg.code, phase = "lobby")
      }
      is IncomingMessage.Joined -> {
        // Player only — we've been assigned an id and (maybe) a character.
        // The CharacterAssigned message arrives separately.
      }
      is IncomingMessage.CharacterAssigned -> {
        _state.value = _state.value.copy(
          myCharacter = CharacterAssignment(
            characterId = msg.character_id,
            name = msg.name,
            role = msg.role,
            secret = msg.secret,
            objective = msg.objective,
            bluffs = msg.bluffs,
          ),
        )
      }
      is IncomingMessage.LobbyUpdate -> {
        _state.value = _state.value.copy(
          code = msg.code,
          menuId = msg.menu_id,
          phase = msg.phase,
          players = msg.players,
        )
      }
      IncomingMessage.GameStarted -> {
        _state.value = _state.value.copy(phase = "playing")
        startSessionService()
      }
      is IncomingMessage.BeatPushed -> {
        val isTwistForMe = msg.beat.kind == "TWIST" && msg.beat.actions != null
        _state.value = _state.value.copy(
          currentBeat = msg.beat,
          currentBeatIndex = msg.beat_index,
          pendingAction = if (isTwistForMe) msg.beat else null,
          phase = "playing",
        )
        scheduleBeatDismiss()
        // Doctrine: phones go face-down on the table between beats. Every
        // beat must vibrate the phone via a notification so the player
        // doesn't have to stare at the screen.
        notifications.postBeat(msg.beat.body, _state.value.code)
        startSessionService()
      }
      is IncomingMessage.HostView -> {
        // Director's view — same beat, but for the host. We just surface it.
        _state.value = _state.value.copy(
          currentBeat = msg.beat,
          currentBeatIndex = msg.beat_index,
          phase = "playing",
        )
      }
      is IncomingMessage.PlayerActionRecorded -> {
        // Host echo of a player's choice. Surface to the host director view
        // so they can see WHO chose WHAT in real time. Audit Fix #3
        // (2026-05-16). Only meaningful on the host; players ignore.
        if (_state.value.role == "host") {
          val entry = HostPlayerActionLogEntry(
            beatIndex = msg.beat_index,
            actionId = msg.action_id,
            playerName = msg.player_name,
            characterId = msg.character_id,
            atEpochMs = System.currentTimeMillis(),
          )
          val trimmed = (_state.value.hostActionLog + entry).takeLast(20)
          _state.value = _state.value.copy(hostActionLog = trimmed)
        }
      }
      is IncomingMessage.Reveal -> {
        _state.value = _state.value.copy(
          phase = "reveal",
          revealText = msg.body,
          revealKillerId = msg.killer_character_id,
        )
        stopSessionService()
      }
      IncomingMessage.Pause -> _state.value = _state.value.copy(phase = "paused")
      IncomingMessage.Resume -> _state.value = _state.value.copy(phase = "playing")
      IncomingMessage.Pong -> Unit
      is IncomingMessage.ServerError -> {
        _state.value = _state.value.copy(errorMessage = msg.message)
      }
      is IncomingMessage.Unknown -> Unit
    }
  }

  /** Pretty name for the menu currently being hosted/played. */
  val menuTitle: String
    get() = findMenu(_state.value.menuId)?.title ?: ""
}
