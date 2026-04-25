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
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
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

/** Brimm Pro SKUs registered in Play Console. Must match exactly. */
const val SKU_PRO_MONTHLY = "brimm_pro_monthly"
const val SKU_PRO_YEARLY = "brimm_pro_yearly"
const val SKU_PRO_LIFETIME = "brimm_pro_lifetime"

private val SUBSCRIPTION_SKUS = listOf(SKU_PRO_MONTHLY, SKU_PRO_YEARLY)
private val INAPP_SKUS = listOf(SKU_PRO_LIFETIME)

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
    runCatching {
      val subResult = client.queryProductDetails(
        QueryProductDetailsParams.newBuilder().setProductList(subRows).build()
      )
      subResult.productDetailsList?.forEach { all[it.productId] = it }
    }
    runCatching {
      val inappResult = client.queryProductDetails(
        QueryProductDetailsParams.newBuilder().setProductList(inappRows).build()
      )
      inappResult.productDetailsList?.forEach { all[it.productId] = it }
    }
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
    if (!awaitReady()) error("Play Billing not connected")
    val product = _products.value[sku] ?: run {
      // Try a refresh in case it landed late.
      queryProducts()
      _products.value[sku] ?: error("Product $sku not found in Play Console — verify it's created and active")
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
    scope.launch {
      // 1. Server-side verification — backend checks the token against Google Play Developer API
      //    and (on success) writes an entitlement row.
      runCatching {
        api.verifyPurchase(
          app.pantrie.network.dto.VerifyPurchaseRequest(
            purchaseToken = purchase.purchaseToken,
            productId = productId,
            packageName = context.packageName,
          )
        )
      }.onSuccess {
        // 2. Mark Pro locally so UI flips immediately + persist.
        entitlement.markProAfterPurchase()
        if (!alreadyOnDevice) _purchaseEvents.value = PurchaseEvent.Success(productId)

        // 3. Acknowledge with Google so the purchase isn't auto-refunded after 3 days.
        if (!purchase.isAcknowledged) {
          runCatching {
            client.acknowledgePurchase(
              AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            )
          }
        }
      }.onFailure { e ->
        if (!alreadyOnDevice) {
          _purchaseEvents.value = PurchaseEvent.Error(
            "Purchase verified by Play but server rejected it: ${e.message ?: "unknown"}. " +
              "Reopen the app — it will retry. If this persists, contact support@brimmapp.com."
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
