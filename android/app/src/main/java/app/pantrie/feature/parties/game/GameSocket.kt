package app.pantrie.feature.parties.game

import android.util.Log
import app.pantrie.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameSocket"

/**
 * WebSocket wrapper around the Cloudflare GameRoom Durable Object.
 *
 * Single-connection client — open one socket per game session. Host opens with
 * role=host; players open with role=player. The same code maps to the same
 * Durable Object on the server.
 *
 * Outgoing messages: typed helper functions (hostCreate / playerJoin / etc.).
 * Incoming messages: collected via [incoming] SharedFlow as parsed
 * [IncomingMessage] sealed-class instances.
 *
 * Connection state: [state] StateFlow for UI surface (loading spinner, etc.).
 */
@Singleton
class GameSocket @Inject constructor() {
  // Self-contained OkHttp client — avoids needing a Hilt module for OkHttpClient.
  // 30s ping keeps the connection alive across mobile-network NATs / proxies.
  private val http: OkHttpClient = OkHttpClient.Builder()
    .pingInterval(30, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MILLISECONDS) // WS reads can hang indefinitely
    .build()

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
  }

  private var ws: WebSocket? = null

  private val _state = MutableStateFlow(GameConnectionState.DISCONNECTED)
  val state: StateFlow<GameConnectionState> = _state.asStateFlow()

  // SharedFlow not StateFlow so each subscriber sees every event in order.
  private val _incoming = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 32)
  val incoming: SharedFlow<IncomingMessage> = _incoming.asSharedFlow()

  /** Open WebSocket to a room. Role = "host" or "player". */
  fun connect(code: String, role: String, name: String, userId: String? = null) {
    val baseHttp = BuildConfig.API_BASE_URL.trimEnd('/')
    val wsBase = baseHttp.replace("https://", "wss://").replace("http://", "ws://")
    val encodedName = URLEncoder.encode(name, "UTF-8")
    val encodedUser = URLEncoder.encode(userId.orEmpty(), "UTF-8")
    val url = "$wsBase/games/$code/ws?role=$role&name=$encodedName&user_id=$encodedUser"

    Log.i(TAG, "connect: $url")
    _state.value = GameConnectionState.CONNECTING

    val request = Request.Builder().url(url).build()
    ws = http.newWebSocket(request, object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.i(TAG, "ws open")
        _state.value = GameConnectionState.CONNECTED
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "ws msg: $text")
        val parsed = parseIncoming(text)
        _incoming.tryEmit(parsed)
      }

      override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        onMessage(webSocket, bytes.utf8())
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.i(TAG, "ws closing: $code $reason")
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.i(TAG, "ws closed: $code $reason")
        _state.value = GameConnectionState.DISCONNECTED
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.e(TAG, "ws failure: ${t.message}", t)
        _state.value = GameConnectionState.ERROR
        _incoming.tryEmit(IncomingMessage.ServerError(t.message ?: "connection failed"))
      }
    })
  }

  fun close() {
    ws?.close(1000, "client closing")
    ws = null
    _state.value = GameConnectionState.DISCONNECTED
  }

  // ---- Outgoing message helpers (host) ----

  fun sendHostCreate(menuId: String, script: GameScript) {
    val payload = buildJsonObject {
      put("t", "host_create")
      put("menu_id", menuId)
      put("script", json.encodeToJsonElement(script))
    }
    sendJson(payload)
  }

  fun sendHostBegin() = sendJson(buildJsonObject { put("t", "host_begin") })
  fun sendHostAdvance() = sendJson(buildJsonObject { put("t", "host_advance") })
  fun sendHostPause() = sendJson(buildJsonObject { put("t", "host_pause") })
  fun sendHostResume() = sendJson(buildJsonObject { put("t", "host_resume") })

  // ---- Outgoing message helpers (player) ----

  fun sendPlayerJoin(code: String, name: String) {
    val payload = buildJsonObject {
      put("t", "player_join")
      put("code", code)
      put("name", name)
    }
    sendJson(payload)
  }

  fun sendPlayerAction(beatIndex: Int, actionId: String) {
    val payload = buildJsonObject {
      put("t", "player_action")
      put("beat_index", beatIndex)
      put("action_id", actionId)
    }
    sendJson(payload)
  }

  private fun sendJson(obj: JsonObject) {
    val text = obj.toString()
    val sent = ws?.send(text) ?: false
    if (!sent) Log.w(TAG, "ws send failed (no socket or buffer full): $text")
  }

  // ---- Incoming message parser ----

  private fun parseIncoming(text: String): IncomingMessage {
    return runCatching {
      val obj = Json.parseToJsonElement(text).jsonObject
      val t = obj["t"]?.jsonPrimitive?.content ?: return@runCatching IncomingMessage.Unknown(text)
      when (t) {
        "connected" -> IncomingMessage.Connected(
          role = obj["role"]?.jsonPrimitive?.content.orEmpty(),
          code = obj["code"]?.jsonPrimitive?.content.orEmpty(),
        )
        "room_created" -> IncomingMessage.RoomCreated(
          code = obj["code"]?.jsonPrimitive?.content.orEmpty(),
        )
        "joined" -> {
          val you = obj["you"]?.jsonObject ?: return@runCatching IncomingMessage.Unknown(text)
          IncomingMessage.Joined(
            player = LobbyPlayer(
              id = you["id"]?.jsonPrimitive?.content.orEmpty(),
              name = you["name"]?.jsonPrimitive?.content.orEmpty(),
              character_id = you["character_id"]?.jsonPrimitive?.contentOrEmpty(),
            )
          )
        }
        "character_assigned" -> IncomingMessage.CharacterAssigned(
          character_id = obj["character_id"]?.jsonPrimitive?.content.orEmpty(),
          name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
          role = obj["role"]?.jsonPrimitive?.content.orEmpty(),
          secret = obj["secret"]?.jsonPrimitive?.content.orEmpty(),
          objective = obj["objective"]?.jsonPrimitive?.content.orEmpty(),
          bluffs = (obj["bluffs"]?.jsonArray ?: emptyList<JsonElement>()).mapNotNull {
            runCatching { it.jsonPrimitive.content }.getOrNull()
          },
        )
        "lobby_update" -> IncomingMessage.LobbyUpdate(
          code = obj["code"]?.jsonPrimitive?.content.orEmpty(),
          menu_id = obj["menu_id"]?.jsonPrimitive?.content.orEmpty(),
          phase = obj["phase"]?.jsonPrimitive?.content.orEmpty(),
          players = (obj["players"]?.jsonArray ?: emptyList<JsonElement>()).map { el ->
            val p = el.jsonObject
            LobbyPlayer(
              id = p["id"]?.jsonPrimitive?.content.orEmpty(),
              name = p["name"]?.jsonPrimitive?.content.orEmpty(),
              character_id = p["character_id"]?.jsonPrimitive?.contentOrEmpty(),
            )
          },
        )
        "game_started" -> IncomingMessage.GameStarted
        "beat_pushed" -> {
          val beatIndex = obj["beat_index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
          val beat = json.decodeFromJsonElement(GameBeat.serializer(), obj["beat"]!!)
          IncomingMessage.BeatPushed(beat_index = beatIndex, beat = beat)
        }
        "host_view" -> {
          val beatIndex = obj["beat_index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
          val beat = json.decodeFromJsonElement(GameBeat.serializer(), obj["beat"]!!)
          IncomingMessage.HostView(beat_index = beatIndex, beat = beat)
        }
        "player_action_recorded" -> IncomingMessage.PlayerActionRecorded(
          beat_index = obj["beat_index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
          action_id = obj["action_id"]?.jsonPrimitive?.content.orEmpty(),
        )
        "reveal" -> IncomingMessage.Reveal(
          body = obj["body"]?.jsonPrimitive?.content.orEmpty(),
          killer_character_id = obj["killer_character_id"]?.jsonPrimitive?.contentOrEmpty(),
          ending_id = obj["ending_id"]?.jsonPrimitive?.contentOrEmpty(),
        )
        "pause" -> IncomingMessage.Pause
        "resume" -> IncomingMessage.Resume
        "pong" -> IncomingMessage.Pong
        "error" -> IncomingMessage.ServerError(
          message = obj["message"]?.jsonPrimitive?.content.orEmpty(),
        )
        else -> IncomingMessage.Unknown(text)
      }
    }.getOrElse { IncomingMessage.Unknown(text) }
  }
}

// Small safe-content extension used in parsing.
private fun kotlinx.serialization.json.JsonPrimitive.contentOrEmpty(): String? =
  runCatching { this.content }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }
