package com.slimepop.asmr

object Catalog {
    const val REMOVE_ADS = "remove_ads"

    // Dynamically pull IDs from the catalogs to ensure alignment with Play Console
    val SKINS: List<String> = SkinCatalog.skins.map { it.id }
    val SOUNDS: List<String> = SoundCatalog.sounds.map { it.id }
    
    val BUNDLES: List<String> = (1..20).map { "bundle_%02d".format(it) }

    val ALL: List<String> = listOf(REMOVE_ADS) + SKINS + SOUNDS + BUNDLES
}
