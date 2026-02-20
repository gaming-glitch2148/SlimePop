package com.slimepop.asmr

object Monetization {
    // Set to true if you decide to manage individual content SKUs in Play Console later.
    const val USE_PLAY_SKUS_FOR_CONTENT = true

    private const val PREMIUM_SKIN_COINS = 12000
    private const val PREMIUM_SOUND_COINS = 3500

    fun requiresPlayPurchase(productId: String): Boolean {
        if (productId == Catalog.REMOVE_ADS) return true
        if (!USE_PLAY_SKUS_FOR_CONTENT) return false

        val skin = SkinCatalog.skins.find { it.id == productId }
        if (skin != null) return skin.isIAP

        val sound = SoundCatalog.sounds.find { it.id == productId }
        if (sound != null) return sound.isIAP

        return false
    }

    fun coinPriceFor(productId: String): Int? {
        val skin = SkinCatalog.skins.find { it.id == productId }
        if (skin != null) {
            if (requiresPlayPurchase(productId)) return null
            return if (skin.coinPrice > 0) skin.coinPrice else if (skin.isIAP) PREMIUM_SKIN_COINS else 0
        }

        val sound = SoundCatalog.sounds.find { it.id == productId }
        if (sound != null) {
            if (requiresPlayPurchase(productId)) return null
            return if (sound.coinPrice > 0) sound.coinPrice else if (sound.isIAP) PREMIUM_SOUND_COINS else 0
        }

        return null
    }

    fun priceLabelFor(productId: String): String? {
        if (requiresPlayPurchase(productId)) return null
        val coins = coinPriceFor(productId) ?: return null
        return if (coins <= 0) "Free" else "$coins Coins"
    }

    fun subtitleForSkin(skin: SlimeSkin): String =
        if (requiresPlayPurchase(skin.id)) "Premium Purchase" else "Gameplay Unlock"

    fun subtitleForSound(sound: SlimeSound): String =
        if (requiresPlayPurchase(sound.id)) "ASMR Soundscape (Premium)" else "ASMR Soundscape Unlock"

    fun billingProductIds(): List<String> {
        if (!USE_PLAY_SKUS_FOR_CONTENT) return listOf(Catalog.REMOVE_ADS)

        val contentSkus = SkinCatalog.skins.filter { it.isIAP }.map { it.id } +
            SoundCatalog.sounds.filter { it.isIAP }.map { it.id }

        return listOf(Catalog.REMOVE_ADS) + contentSkus
    }
}
