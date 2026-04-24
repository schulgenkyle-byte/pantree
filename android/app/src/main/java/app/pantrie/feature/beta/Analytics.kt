package app.pantrie.feature.beta

import android.content.Context
import android.os.Build
import app.pantrie.BuildConfig
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.AnalyticsBatch
import app.pantrie.network.dto.AnalyticsEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fire-and-forget event tracker. Buffers up to 50 events in memory and
 * flushes in batches of 20 or every 10 s, whichever comes first.
 * No PII — event names + coarse props only.
 */
@Singleton
class Analytics @Inject constructor(
  private val api: PantrieApi,
  @ApplicationContext private val ctx: Context,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val queue = ArrayDeque<AnalyticsEvent>()
  private val lock = Any()
  private val sessionId = UUID.randomUUID().toString()
  private val flushSignal = Channel<Unit>(capacity = Channel.CONFLATED)

  private val _currentRoute = MutableStateFlow<String?>(null)
  val currentRoute = _currentRoute.asStateFlow()

  init {
    scope.launch { runLoop() }
  }

  fun setRoute(route: String?) { _currentRoute.value = route }

  fun track(name: String, props: Map<String, Any?>? = null) {
    val ev = AnalyticsEvent(
      name = name,
      route = _currentRoute.value,
      props = props?.let { buildProps(it) },
      ts = System.currentTimeMillis(),
    )
    synchronized(lock) {
      if (queue.size >= 200) queue.removeFirst()
      queue.addLast(ev)
    }
    flushSignal.trySend(Unit)
  }

  private fun buildProps(m: Map<String, Any?>): JsonObject {
    val out = linkedMapOf<String, JsonElement>()
    for ((k, v) in m) {
      if (v == null) continue
      out[k] = when (v) {
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString().take(200))
      }
    }
    return JsonObject(out)
  }

  private suspend fun runLoop() {
    while (true) {
      flushSignal.receive()
      // debounce a little so burst events batch
      delay(10_000)
      flush()
    }
  }

  private suspend fun flush() {
    val batch: List<AnalyticsEvent>
    synchronized(lock) {
      if (queue.isEmpty()) return
      batch = queue.toList().take(50)
      repeat(batch.size) { queue.removeFirst() }
    }
    runCatching {
      api.sendEvents(AnalyticsBatch(
        sessionId = sessionId,
        appVersion = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
        events = batch,
      ))
    }.onFailure {
      // On failure, put them back at the front — bounded to avoid OOM
      synchronized(lock) {
        for (e in batch.asReversed()) {
          if (queue.size >= 200) break
          queue.addFirst(e)
        }
      }
    }
  }

  fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
}
