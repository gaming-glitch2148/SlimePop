package com.slimepop.asmr

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

class BillingManager(
    private val context: Context,
    private val onEntitlements: (Entitlements) -> Unit
) : PurchasesUpdatedListener {

    private var client: BillingClient? = null
    private val detailsById = mutableMapOf<String, ProductDetails>()

    fun start() {
        client = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails(Monetization.billingProductIds())
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

    fun launchPurchase(activity: Activity, productId: String) {
        val c = client ?: return
        val details = detailsById[productId] ?: return

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        c.launchBillingFlow(activity, flow)
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

    private fun queryProductDetails(ids: List<String>) {
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
                detailsById.clear()
                list?.forEach { detailsById[it.productId] = it }
            }
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
