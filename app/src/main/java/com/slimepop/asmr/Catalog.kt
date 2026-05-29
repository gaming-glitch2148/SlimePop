package com.slimepop.asmr

object Catalog {
    const val REMOVE_ADS = "remove_ads"

    // Season pass SKUs — create these 6 once in Play Console, never again.
    // Content they grant is managed entirely in SeasonCatalog.kt.
    const val SEASON_SUMMER   = "season_summer"
    const val SEASON_GEMSTONE = "season_gemstone"
    const val SEASON_FALL     = "season_fall"
    const val SEASON_WINTER   = "season_winter"
    const val SEASON_COSMIC   = "season_cosmic"
    const val SEASON_NEON     = "season_neon"

    val SEASONS: List<String> = SeasonCatalog.seasons.map { it.id }
    val SKINS: List<String>   = SkinCatalog.skins.map { it.id }
    val SOUNDS: List<String>  = SoundCatalog.sounds.map { it.id }
    val BUNDLES: List<String> = (1..20).map { "bundle_%02d".format(it) }

    val ALL: List<String> = listOf(REMOVE_ADS) + SEASONS + SKINS + SOUNDS + BUNDLES
}
