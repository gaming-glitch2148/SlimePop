package com.slimepop.asmr

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import java.lang.ref.WeakReference

class BillingManager(
    private val context: Context,
    private val onEntitlements: (Entitlements) -> Unit
) : PurchasesUpdatedListener {

    private var client: BillingClient? = null
    private val detailsById = mutableMapOf<String, ProductDetails>()
    private var pendingPurchase: PendingPurchase? = null

    private data class PendingPurchase(
        val activityRef: WeakReference<Activity>,
        val productId: String
    )

    fun start() {
        client = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails(Monetization.billingProductIds(), replaceCache = true)
                    restorePurchases()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    fun end() {
        client?.endConnection()
        client = null
        detailsById.clear()
    }

    fun getFormattedPrice(productId: String): String? =
        detailsById[productId]?.oneTimePurchaseOfferDetails?.formattedPrice

    fun canPurchase(productId: String) = detailsById.containsKey(productId)

    private fun launchPurchaseFlow(activity: Activity, details: ProductDetails): Boolean {
        val c = client ?: return false

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = c.launchBillingFlow(activity, flow)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun launchPurchase(activity: Activity, productId: String): Boolean {
        val details = detailsById[productId]
        if (details != null) {
            return launchPurchaseFlow(activity, details)
        }

        pendingPurchase = PendingPurchase(WeakReference(activity), productId)
        queryProductDetails(listOf(productId), replaceCache = false)
        return false
    }

    fun restorePurchases() {
        val c = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        c.queryPurchasesAsync(params) { _, purchases ->
            handlePurchases(purchases, isRestore = true)
        }
    }

    private fun queryProductDetails(ids: List<String>, replaceCache: Boolean) {
        val products = ids.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        client?.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (replaceCache) {
                    detailsById.clear()
                }
                list?.forEach { detailsById[it.productId] = it }
                attemptPendingPurchase()
            }
        }
    }

    private fun attemptPendingPurchase() {
        val pending = pendingPurchase ?: return
        val details = detailsById[pending.productId] ?: return
        val activity = pending.activityRef.get() ?: run {
            pendingPurchase = null
            return
        }
        val launched = launchPurchaseFlow(activity, details)
        if (launched) {
            pendingPurchase = null
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases, isRestore = false)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>, isRestore: Boolean) {
        val previousOwned = EntitlementResolver.ownedSetFromCsv(Prefs.getOwnedIapCsv(context))
        val owned = purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }
            .toSet()
        val newlyOwned = owned - previousOwned

        Prefs.setOwnedIapCsv(context, owned.sorted().joinToString(","))
        Prefs.setAdsRemoved(context, owned.contains(Catalog.REMOVE_ADS))

        if (!isRestore) {
            newlyOwned.forEach { productId ->
                RevenueTelemetry.trackPurchaseConfirmed(
                    context,
                    productId,
                    RevenueTelemetry.PurchaseSource.PLAY_BILLING
                )
            }
        }

        val ent = EntitlementResolver.resolveFromOwnedProducts(owned)
        onEntitlements(ent)

        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { p ->
                val ack = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(p.purchaseToken)
                    .build()
                client?.acknowledgePurchase(ack) { }
            }
    }
}
