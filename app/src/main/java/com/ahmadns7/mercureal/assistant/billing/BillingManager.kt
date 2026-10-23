package com.ahmadns7.mercureal.assistant.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.ahmadns7.mercureal.assistant.model.Entitlements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-time "MercuReal Pro" purchase through Google Play Billing.
 * Purchase state is cached in [Entitlements]; on every launch we re-query Play so a refund or
 * a restored purchase is picked up. Nothing here sends speech, prompts or history anywhere:
 * Play only sees the product id and the purchase token.
 */
class BillingManager(context: Context, private val entitlements: Entitlements) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases()
        .build()

    private val _product = MutableStateFlow<ProductDetails?>(null)
    val product: StateFlow<ProductDetails?> = _product.asStateFlow()

    private val _status = MutableStateFlow("")
    /** Short human status for the upsell screen, e.g. "store unavailable". */
    val status: StateFlow<String> = _status.asStateFlow()

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refresh()
                } else {
                    _status.value = "google play unavailable here (${result.debugMessage.ifBlank { "code ${result.responseCode}" }})"
                }
            }

            override fun onBillingServiceDisconnected() {
                _status.value = "reconnecting to google play"
            }
        })
    }

    /** Loads the Pro product details and restores any existing owned purchase. */
    fun refresh() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingConfig.PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && details.isNotEmpty()) {
                _product.value = details.first()
                _status.value = ""
            } else {
                _product.value = null
                _status.value = "product \"${BillingConfig.PRODUCT_ID}\" not found on google play yet"
            }
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any { isProPurchase(it) }
                scope.launch {
                    purchases.filter { isProPurchase(it) && !it.isAcknowledged }.forEach { acknowledge(it) }
                    entitlements.setPurchased(owned)
                }
            }
        }
    }

    /** Opens the Play purchase sheet. Returns false if the product is not loaded yet. */
    fun launchPurchase(activity: Activity): Boolean {
        val details = _product.value ?: return false
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            ).build()
        val result = client.launchBillingFlow(activity, flow)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        scope.launch {
            purchases.filter { isProPurchase(it) }.forEach { purchase ->
                if (!purchase.isAcknowledged) acknowledge(purchase)
                entitlements.setPurchased(true)
            }
        }
    }

    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { }
    }

    private fun isProPurchase(p: Purchase): Boolean =
        p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.contains(BillingConfig.PRODUCT_ID)

    fun release() {
        client.endConnection()
    }
}
