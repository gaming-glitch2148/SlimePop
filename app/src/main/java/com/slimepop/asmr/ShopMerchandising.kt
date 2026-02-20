package com.slimepop.asmr

object ShopMerchandising {
    enum class Variant {
        PREMIUM_FIRST,
        VALUE_STACK,
        CONTROL
    }

    private val featuredSkins = listOf(
        "skin_gold", "skin_galaxy", "skin_dragon_scale", "skin_holo_prism",
        "skin_obsidian", "skin_nova", "skin_aurora_glass", "skin_ruby"
    )

    private val featuredSounds = listOf(
        "sound_005", "sound_012", "sound_020", "sound_028",
        "sound_037", "sound_043", "sound_049", "sound_050"
    )

    private val featuredOrder = (featuredSkins + featuredSounds).withIndex().associate { it.value to it.index }

    fun parseVariant(raw: String?): Variant? = when (raw?.trim()?.uppercase()) {
        "PREMIUM_FIRST" -> Variant.PREMIUM_FIRST
        "VALUE_STACK" -> Variant.VALUE_STACK
        "CONTROL" -> Variant.CONTROL
        else -> null
    }

    fun rankAndBadge(
        items: List<ShopItem>,
        variant: Variant,
        entitlements: Entitlements,
        coinPriceLookup: (String) -> Int?,
        requiresPlayPurchase: (String) -> Boolean
    ): List<ShopItem> {
        val decorated = items.map { item ->
            val owned = isOwned(item, entitlements)
            val badge = badgeFor(item, owned, variant, coinPriceLookup, requiresPlayPurchase)
            item.copy(badge = badge)
        }

        val indexed = decorated.withIndex()
        val sorted = indexed.sortedWith(compareByDescending<IndexedValue<ShopItem>> { scoreFor(it.value, it.index, variant, entitlements, coinPriceLookup, requiresPlayPurchase) }
            .thenBy { it.index })
        return sorted.map { it.value }
    }

    private fun isOwned(item: ShopItem, entitlements: Entitlements): Boolean = when (item.category) {
        ShopCategory.SKIN, ShopCategory.SOUND -> entitlements.ownedContent.contains(item.productId)
        ShopCategory.BUNDLE, ShopCategory.REMOVE_ADS -> entitlements.ownedProducts.contains(item.productId)
    }

    private fun badgeFor(
        item: ShopItem,
        owned: Boolean,
        variant: Variant,
        coinPriceLookup: (String) -> Int?,
        requiresPlayPurchase: (String) -> Boolean
    ): String? {
        if (owned) return "OWNED"
        if (item.productId == Catalog.REMOVE_ADS) return "TOP UPGRADE"
        if (featuredOrder.containsKey(item.productId)) return "BEST SELLER"

        val coinPrice = coinPriceLookup(item.productId)
        if (variant == Variant.VALUE_STACK && coinPrice != null && coinPrice in 1..3500) return "STARTER DEAL"
        if (requiresPlayPurchase(item.productId)) return "PREMIUM"
        return null
    }

    private fun scoreFor(
        item: ShopItem,
        index: Int,
        variant: Variant,
        entitlements: Entitlements,
        coinPriceLookup: (String) -> Int?,
        requiresPlayPurchase: (String) -> Boolean
    ): Int {
        var score = 0
        val owned = isOwned(item, entitlements)
        if (!owned) score += 10000

        if (item.productId == Catalog.REMOVE_ADS) score += if (variant == Variant.PREMIUM_FIRST) 9000 else 3000
        if (featuredOrder.containsKey(item.productId)) score += 5000 - (featuredOrder[item.productId] ?: 0)

        when (variant) {
            Variant.PREMIUM_FIRST -> {
                if (requiresPlayPurchase(item.productId)) score += 3200
                val coin = coinPriceLookup(item.productId)
                if (coin != null && coin > 0) score += (4000 - coin).coerceAtLeast(0) / 3
            }
            Variant.VALUE_STACK -> {
                val coin = coinPriceLookup(item.productId)
                if (coin == 0) score += 3000
                if (coin != null && coin > 0) score += (6000 - coin).coerceAtLeast(0)
                if (requiresPlayPurchase(item.productId)) score += 600
            }
            Variant.CONTROL -> {
                score += (2000 - index).coerceAtLeast(0)
            }
        }

        return score
    }
}
