package app.pantrie.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speakeater Pro SKUs registered in Play Console. Must match exactly.
 *
 * The `brimm_*` prefix is LOCKED — Play Console SKU ids cannot be renamed without
 * orphaning every existing subscriber. The user-facing brand display name lives in
 * Brand.PRO_NAME ("Speakeater Pro"); only the internal SKU id strings keep the
 * legacy prefix.
 */
// V3 SKUs — active in Play Console as of 2026-05-17. $5/mo + $45/yr.
// V1 SKUs (brimm_pro_monthly @ $4.99 / brimm_pro_yearly @ $50) and V2 SKUs
// (brimm_pro_monthly_v2 @ $14.99 / brimm_pro_yearly_v2 @ $129) remain Active
// in Play Console for grandfathered subscribers — backend/src/billing-skus.js
// keeps all versions on the allowlist so existing renewals still verify.
// New purchases flow through these v3 SKUs only.
//
// V3 PLAY CONSOLE SETUP REQUIRED before this build hits production:
//   1. Create subscription `speakeater_pro_monthly_v3`, base plan `monthly`, $5/mo
//   2. Create subscription `speakeater_pro_yearly_v3`, base plan `yearly`, $45/yr
//   3. Deactivate v2 SKUs for NEW purchases (keep Active for renewals)
//   4. Deactivate any `brimm_pro_lifetime` / `speakeater_pro_lifetime` SKU
//      — lifetime Pro is killed in V3, no replacement
const val SKU_PRO_MONTHLY = "speakeater_pro_monthly_v3"
const val SKU_PRO_YEARLY = "speakeater_pro_yearly_v3"

// Legacy SKU constants kept for grandfathered-subscriber detection.
const val SKU_PRO_MONTHLY_V2 = "brimm_pro_monthly_v2"
const val SKU_PRO_YEARLY_V2 = "brimm_pro_yearly_v2"
const val SKU_PRO_MONTHLY_V1 = "brimm_pro_monthly"
const val SKU_PRO_YEARLY_V1 = "brimm_pro_yearly"

// Image scan top-up — consumable IAP, $2 grants 20 fridge scans.
// Server adds credits to user_scan_credits on verify. Free users see this
// in the upgrade prompt when they hit the monthly 5-scan cap.
// REQUIRES Play Console SKU creation: in-app product `speakeater_scans_20` @ $1.99
const val SKU_SCANS_20 = "speakeater_scans_20"

// Consumable credit packs — 1920s speakeasy naming. Buy once, get extra
// swipes + photo scans on top of whatever tier you're on. Generous on
// swipes (cheap to serve), tight on photos (~$0.005 / Vision call so we
// keep margin even at the largest pack). User keeps unconsumed credits
// across purchases — they accumulate.
//
// NOTE: Credit packs are currently DISABLED end-to-end via
// [CREDIT_PACKS_ENABLED] below. Server-side credit-grant logic is not yet
// implemented (no user_credits table, no decrement hooks at swipe/photo
// usage sites). The paywall UI hides these tiles until the flag flips.
// Backend mirror: backend/src/billing-skus.js — keep the constants in sync.
const val SKU_PACK_NIGHTCAP   = "speakeater_pack_nightcap"   // $1.99 — 50 swipes + 5 photos
const val SKU_PACK_BOOTLEGGER = "speakeater_pack_bootlegger" // $4.99 — 200 swipes + 15 photos
const val SKU_PACK_GATSBY     = "speakeater_pack_gatsby"     // $9.99 — 500 swipes + 40 photos

/**
 * Feature flag for the credit-pack purchase flow. False until the backend
 * `user_credits` table + grant/decrement logic ships AND the Play Console
 * in-app products are created and activated. When flipping to true:
 *   1. Populate `ALLOWED_INAPP_SKUS` + `CREDIT_PACK_GRANTS` in
 *      `backend/src/billing-skus.js`.
 *   2. Add credit-grant logic to `backend/src/billing.js` (currently the
 *      `verify` route returns 503 'credit packs not yet available').
 *   3. Add server-side decrement at every usage site (swipe, photo scan).
 *   4. Create the three SKUs in Play Console → In-app products.
 */
const val CREDIT_PACKS_ENABLED = false

/** Represents a credit pack's value proposition for the paywall UI.
 *  Prices are placeholders; the real localized price comes from
 *  ProductDetails once Play Console returns the SKU. */
data class CreditPack(
  val sku: String,
  val displayName: String,
  val tagline: String,
  val swipes: Int,
  val photoScans: Int,
  val priceFallback: String,  // shown if Play Console hasn't loaded the SKU yet
)

val CREDIT_PACKS = listOf(
  CreditPack(SKU_PACK_NIGHTCAP,   "The Nightcap",         "A quick one before bed.",   50,  5,  "$1.99"),
  CreditPack(SKU_PACK_BOOTLEGGER, "The Bootlegger's Run", "Stocked for the week.",     200, 15, "$4.99"),
  CreditPack(SKU_PACK_GATSBY,     "The Gatsby",           "Thrown for the whole crew.", 500, 40, "$9.99"),
)

private val SUBSCRIPTION_SKUS = listOf(SKU_PRO_MONTHLY, SKU_PRO_YEARLY)
private val INAPP_SKUS = listOf(SKU_PACK_NIGHTCAP, SKU_PACK_BOOTLEGGER, SKU_PACK_GATSBY)

/**
 * Wraps the Google Play BillingClient. Single-tenant: holds one client per app process.
 *
 * Flow:
 *   1. Init connects to Play. Once ready, queries product details for all known SKUs.
 *   2. UI calls launchPurchase(activity, sku). BillingClient pops Google's purchase sheet.
 *   3. Google fires onPurchasesUpdated. We forward each new purchase to the backend
 *      for server-side verification + entitlement grant, then acknowledge with Google.
 *   4. On every app foreground, queryPurchases() reconciles in case Google missed a callback.
 */
@Singleton
class BillingManager @Inject constructor(
  @ApplicationContext private val context: Context,
  private val api: app.pantrie.network.PantrieApi,
  private val entitlement: EntitlementRepository,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
  val products = _products.asStateFlow()

  private val _purchaseEvents = MutableStateFlow<PurchaseEvent?>(null)
  val purchaseEvents = _purchaseEvents.asStateFlow()
  fun consumePurchaseEvent() { _purchaseEvents.value = null }
  /** Push a manual error into the event stream so UIs that don't capture launchPurchase()'s
   * Result can still surface what went wrong. Used by PaywallViewModel when the launch call
   * itself throws (e.g. product not found in Play Console yet). */
  fun surfaceError(message: String) { _purchaseEvents.value = PurchaseEvent.Error(message) }

  private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
    when {
      result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty() -> {
        purchases.forEach { handleNewPurchase(it) }
      }
      result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> {
        _purchaseEvents.value = PurchaseEvent.Cancelled
      }
      else -> {
        _purchaseEvents.value = PurchaseEvent.Error(
          "Purchase failed (${result.responseCode}): ${result.debugMessage.orEmpty()}"
        )
      }
    }
  }

  private val client: BillingClient = BillingClient.newBuilder(context)
    .setListener(purchasesListener)
    .enablePendingPurchases(
      com.android.billingclient.api.PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()
    )
    .build()

  private val ready = CompletableDeferred<Boolean>()

  init {
    connect()
  }

  private fun connect() {
    client.startConnection(object : BillingClientStateListener {
      override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
          ready.complete(true)
          scope.launch {
            queryProducts()
            // Reconcile any purchases Play already knows about (e.g. user reinstalled app
            // mid-subscription, or the previous purchase callback was dropped).
            reconcileExistingPurchases()
          }
        } else {
          if (!ready.isCompleted) ready.complete(false)
        }
      }
      override fun onBillingServiceDisconnected() {
        // Reconnect on next purchase attempt — don't hammer Play with retries here.
      }
    })
  }

  private suspend fun awaitReady(): Boolean {
    return runCatching { ready.await() }.getOrDefault(false)
  }

  private suspend fun queryProducts() {
    val subRows = SUBSCRIPTION_SKUS.map {
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(it)
        .setProductType(BillingClient.ProductType.SUBS)
        .build()
    }
    val inappRows = INAPP_SKUS.map {
      QueryProductDetailsParams.Product.newBuilder()
        .setProductId(it)
        .setProductType(BillingClient.ProductType.INAPP)
        .build()
    }
    val all = mutableMapOf<String, ProductDetails>()
    val errors = mutableListOf<String>()
    runCatching {
      val subResult = client.queryProductDetails(
        QueryProductDetailsParams.newBuilder().setProductList(subRows).build()
      )
      val billingResult = subResult.billingResult
      if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        errors += "subs query: code=${billingResult.responseCode} msg=${billingResult.debugMessage}"
      }
      subResult.productDetailsList?.forEach { all[it.productId] = it }
    }.onFailure { errors += "subs query threw: ${it.message ?: it::class.simpleName}" }
    runCatching {
      val inappResult = client.queryProductDetails(
        QueryProductDetailsParams.newBuilder().setProductList(inappRows).build()
      )
      val billingResult = inappResult.billingResult
      if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
        errors += "inapp query: code=${billingResult.responseCode} msg=${billingResult.debugMessage}"
      }
      inappResult.productDetailsList?.forEach { all[it.productId] = it }
    }.onFailure { errors += "inapp query threw: ${it.message ?: it::class.simpleName}" }

    android.util.Log.i(
      "Billing",
      "queryProducts: found ${all.size}/${SUBSCRIPTION_SKUS.size + INAPP_SKUS.size} " +
        "(${all.keys.joinToString(",")}); errors: ${errors.joinToString(" | ").ifEmpty { "none" }}"
    )
    _products.value = all
  }

  private suspend fun reconcileExistingPurchases() {
    runCatching {
      val subs = client.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
      )
      subs.purchasesList.forEach { handleNewPurchase(it, alreadyOnDevice = true) }
    }
    runCatching {
      val inapps = client.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
      )
      inapps.purchasesList.forEach { handleNewPurchase(it, alreadyOnDevice = true) }
    }
  }

  /**
   * Pops Google's purchase sheet for [sku]. Caller passes the current activity.
   * Result lands asynchronously via [purchaseEvents] (the PurchasesUpdatedListener callback).
   */
  suspend fun launchPurchase(activity: Activity, sku: String): Result<Unit> = runCatching {
    android.util.Log.i("Billing", "launchPurchase($sku) start; products cached: ${_products.value.keys}")
    if (!awaitReady()) error("Play Billing not connected — Google Play Services unavailable. Reboot phone or update Play Store app.")
    val product = _products.value[sku] ?: run {
      // Try a refresh in case it landed late.
      android.util.Log.w("Billing", "launchPurchase($sku): product not in cache, retrying queryProducts()")
      queryProducts()
      _products.value[sku] ?: error(
        "Product '$sku' not found in Google Play. Most likely Play Console subscription/in-app product " +
          "isn't created or activated yet. Check Play Console → Monetization → Subscriptions / In-app products."
      )
    }

    // For subscriptions, we need an offer token (the specific base plan / offer the user is buying).
    val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
      .setProductDetails(product)
    if (product.productType == BillingClient.ProductType.SUBS) {
      val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
        ?: error("Subscription $sku has no offer token — Play Console offer not active?")
      productParamsBuilder.setOfferToken(offerToken)
    }

    val flowParams = BillingFlowParams.newBuilder()
      .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
      .build()

    val result = client.launchBillingFlow(activity, flowParams)
    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      error("launchBillingFlow failed (${result.responseCode}): ${result.debugMessage.orEmpty()}")
    }
  }

  private fun handleNewPurchase(purchase: Purchase, alreadyOnDevice: Boolean = false) {
    if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return  // PENDING is not yet a purchase
    val productId = purchase.products.firstOrNull() ?: return
    val isCreditPack = productId in INAPP_SKUS
    scope.launch {
      runCatching {
        api.verifyPurchase(
          app.pantrie.network.dto.VerifyPurchaseRequest(
            purchaseToken = purchase.purchaseToken,
            productId = productId,
            packageName = context.packageName,
          )
        )
      }.onSuccess {
        if (isCreditPack) {
          // Consumable credit pack: server credited the user's swipe/photo
          // balance. Consume the purchase with Google so the user can buy
          // the same pack again later. Do NOT mark as Pro — credit packs
          // don't grant subscription status.
          if (!alreadyOnDevice) _purchaseEvents.value = PurchaseEvent.Success(productId)
          runCatching {
            client.consumePurchase(
              ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            )
          }
        } else {
          // Subscription path — flip Pro locally + acknowledge (NOT consume)
          // so the subscription survives across sessions.
          entitlement.markProAfterPurchase()
          if (!alreadyOnDevice) _purchaseEvents.value = PurchaseEvent.Success(productId)
          if (!purchase.isAcknowledged) {
            runCatching {
              client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                  .setPurchaseToken(purchase.purchaseToken)
                  .build()
              )
            }
          }
        }
      }.onFailure { e ->
        if (!alreadyOnDevice) {
          _purchaseEvents.value = PurchaseEvent.Error(
            "Purchase verified by Play but server rejected it: ${e.message ?: "unknown"}. " +
              "Reopen the app — it will retry. If this persists, contact ${app.pantrie.Brand.SUPPORT_EMAIL}."
          )
        }
      }
    }
  }
}

sealed interface PurchaseEvent {
  data class Success(val productId: String) : PurchaseEvent
  data object Cancelled : PurchaseEvent
  data class Error(val message: String) : PurchaseEvent
}
