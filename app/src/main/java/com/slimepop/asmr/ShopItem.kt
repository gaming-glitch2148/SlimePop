package com.slimepop.asmr

enum class ShopCategory { SKIN, SOUND, BUNDLE, REMOVE_ADS }

data class ShopItem(
    val productId: String,
    val category: ShopCategory,
    val title: String,
    val subtitle: String,
    val badge: String? = null
)
