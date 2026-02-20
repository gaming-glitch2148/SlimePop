package com.slimepop.asmr

import android.content.Context
import android.util.Log
import java.util.Locale

object RevenueTelemetry {
    enum class PurchaseSource {
        COINS,
        PLAY_BILLING
    }

    private const val FILE = "slime_pop_telemetry"
    private const val KEY_EVENT_PREFIX = "event_count_"
    private const val KEY_LAST_CLICK_VARIANT_PREFIX = "last_click_variant_"
    private const val TAG = "RevenueTelemetry"

    fun trackImpression(ctx: Context, productId: String, variant: String) {
        incrementEvent(ctx, "impression", productId, variant)
    }

    fun trackBuyClick(ctx: Context, productId: String, variant: String) {
        rememberLastClickVariant(ctx, productId, variant)
        incrementEvent(ctx, "buy_click", productId, variant)
    }

    fun trackEquipClick(ctx: Context, productId: String, variant: String) {
        incrementEvent(ctx, "equip_click", productId, variant)
    }

    fun trackPurchaseConfirmed(
        ctx: Context,
        productId: String,
        source: PurchaseSource,
        variantHint: String? = null
    ) {
        val variant = normalizeVariant(variantHint ?: lastClickVariant(ctx, productId))
        val eventName = "purchase_${source.name.lowercase(Locale.US)}"
        incrementEvent(ctx, eventName, productId, variant)
    }

    private fun incrementEvent(ctx: Context, event: String, productId: String, variant: String) {
        val appCtx = ctx.applicationContext
        val sp = appCtx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val normalizedVariant = normalizeVariant(variant)
        val category = categoryFor(productId)
        val key = "$KEY_EVENT_PREFIX$event|$normalizedVariant|$category|$productId"
        val next = sp.getInt(key, 0) + 1
        sp.edit().putInt(key, next).apply()

        Log.d(
            TAG,
            "event=$event sku=$productId category=$category variant=$normalizedVariant count=$next"
        )
    }

    private fun rememberLastClickVariant(ctx: Context, productId: String, variant: String) {
        val appCtx = ctx.applicationContext
        val sp = appCtx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp.edit().putString("$KEY_LAST_CLICK_VARIANT_PREFIX$productId", normalizeVariant(variant)).apply()
    }

    private fun lastClickVariant(ctx: Context, productId: String): String {
        val appCtx = ctx.applicationContext
        val sp = appCtx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return sp.getString("$KEY_LAST_CLICK_VARIANT_PREFIX$productId", "UNKNOWN") ?: "UNKNOWN"
    }

    private fun normalizeVariant(value: String?): String =
        value?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.US) ?: "UNKNOWN"

    private fun categoryFor(productId: String): String = when {
        productId.startsWith("skin_") -> "skin"
        productId.startsWith("sound_") -> "sound"
        productId == Catalog.REMOVE_ADS -> "remove_ads"
        productId.startsWith("bundle_") -> "bundle"
        else -> "other"
    }
}
