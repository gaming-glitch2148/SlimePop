package com.slimepop.asmr

object Catalog {
    const val REMOVE_ADS = "remove_ads"

    val SKINS: List<String> = (1..50).map { "skin_%03d".format(it) }
    val SOUNDS: List<String> = (1..50).map { "sound_%03d".format(it) }
    val BUNDLES: List<String> = (1..20).map { "bundle_%02d".format(it) }

    val ALL: List<String> = listOf(REMOVE_ADS) + SKINS + SOUNDS + BUNDLES
}