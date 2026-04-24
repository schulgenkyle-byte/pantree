package app.pantrie.network

import app.pantrie.BuildConfig
import app.pantrie.auth.TokenStore
import app.pantrie.network.dto.RefreshRequest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Bearer-auth + 401 refresh. Refresh is serialized behind a Mutex so concurrent 401s
 * don't stampede the backend (and don't race to overwrite each other's rotated token).
 * Refresh runs on a separate OkHttpClient to avoid starving the main dispatcher.
 */
class AuthInterceptor(
  private val tokenStore: TokenStore,
  private val refreshClient: OkHttpClient,
  private val baseUrl: String,
  private val json: Json,
) : Interceptor {

  private val refreshMutex = Mutex()

  override fun intercept(chain: Interceptor.Chain): Response {
    val req = chain.request()
    val path = req.url.encodedPath
    val skipAuth = path.startsWith("/auth/") || path == "/health"

    val first = if (skipAuth) req else withBearer(req, tokenStore.getAccess() ?: singleFlightRefresh())
    val resp = chain.proceed(first)

    if (resp.code == 401 && !skipAuth) {
      resp.close()
      val fresh = singleFlightRefresh()
      if (fresh != null) return chain.proceed(withBearer(req, fresh))
    }
    return resp
  }

  private fun withBearer(req: Request, token: String?): Request =
    if (token == null) req else req.newBuilder().header("Authorization", "Bearer $token").build()

  private fun singleFlightRefresh(): String? = runBlocking {
    refreshMutex.withLock {
      tokenStore.getAccess()?.let { return@withLock it }
      val refresh = tokenStore.getRefreshToken() ?: return@withLock null
      runCatching { refreshOnce(refresh) }.getOrNull()
    }
  }

  private fun refreshOnce(refreshToken: String): String? {
    val bodyJson = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
    val req = Request.Builder()
      .url("${baseUrl.trimEnd('/')}/auth/refresh")
      .post(okhttp3.RequestBody.create("application/json".toMediaType(), bodyJson))
      .build()
    refreshClient.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) {
        if (resp.code in 400..401) tokenStore.clear()
        return null
      }
      val raw = resp.body?.string() ?: return null
      val parsed = json.decodeFromString(app.pantrie.network.dto.SessionResponse.serializer(), raw)
      tokenStore.saveRefreshToken(parsed.refreshToken)
      tokenStore.setAccess(parsed.accessToken, parsed.expiresAt)
      return parsed.accessToken
    }
  }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

  @Provides @Singleton
  fun provideJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true   // server null for non-null field → use default instead of crash
  }

  private fun loggingInterceptor(): HttpLoggingInterceptor =
    HttpLoggingInterceptor { msg ->
      // Funnel through standard Log.i so Logcat filter `PantrieHttp` catches all of it.
      android.util.Log.i("PantrieHttp", msg)
    }.apply {
      level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
      redactHeader("Authorization")
      redactHeader("Cookie")
      redactHeader("Set-Cookie")
      redactHeader("X-Dev-Key")
    }

  /** Pin the API host. Pins are also declared in network_security_config.xml — both enforce. */
  private fun pinner(baseUrl: String): CertificatePinner {
    val host = runCatching { URI(baseUrl).host }.getOrNull() ?: return CertificatePinner.Builder().build()
    val primary = BuildConfig::class.java.fields
      .firstOrNull { it.name == "API_PIN_PRIMARY" }
      ?.get(null) as? String
    val backup = BuildConfig::class.java.fields
      .firstOrNull { it.name == "API_PIN_BACKUP" }
      ?.get(null) as? String
    val b = CertificatePinner.Builder()
    if (!primary.isNullOrBlank()) b.add(host, "sha256/$primary")
    if (!backup.isNullOrBlank()) b.add(host, "sha256/$backup")
    return b.build()
  }

  private val MODERN_SPECS = listOf(
    ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
      .build(),
  )

  private fun baseClient(baseUrl: String): OkHttpClient.Builder =
    OkHttpClient.Builder()
      .connectionSpecs(MODERN_SPECS)
      .certificatePinner(pinner(baseUrl))
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .writeTimeout(60, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)

  @Provides @Singleton
  fun provideOkHttp(
    tokenStore: TokenStore,
    json: Json,
  ): OkHttpClient {
    val baseUrl = BuildConfig.API_BASE_URL
    // Separate client for refresh calls so AuthInterceptor can't deadlock the main dispatcher.
    val refreshClient = baseClient(baseUrl).addInterceptor(loggingInterceptor()).build()
    return baseClient(baseUrl)
      .addInterceptor(AuthInterceptor(tokenStore, refreshClient, baseUrl, json))
      .addInterceptor(loggingInterceptor())
      .build()
  }

  @Provides @Singleton
  fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
    Retrofit.Builder()
      .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
      .client(client)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()

  @Provides @Singleton
  fun provideApi(retrofit: Retrofit): PantrieApi = retrofit.create(PantrieApi::class.java)
}
