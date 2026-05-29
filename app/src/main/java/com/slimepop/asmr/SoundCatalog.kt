package com.slimepop.asmr

import android.graphics.Color

data class SlimeSound(
    val id: String,
    val name: String,
    val description: String,
    val isIAP: Boolean = false,
    val coinPrice: Int = 0,
    val swatchBase: Int = Color.parseColor("#3A86FF"),
    val swatchHighlight: Int = Color.parseColor("#8338EC")
)

object SoundCatalog {
    private fun c(hex: String) = Color.parseColor(hex)

    val sounds = listOf(
        SlimeSound("sound_001", "Soft Rain",        "Gentle rain tapping on a window.",     isIAP = false, coinPrice = 0,  swatchBase = c("#1565C0"), swatchHighlight = c("#82B1FF")),
        SlimeSound("sound_002", "Forest Whispers",  "Calm wind and rustling leaves.",        isIAP = true,  swatchBase = c("#2E7D32"), swatchHighlight = c("#A5D6A7")),
        SlimeSound("sound_003", "Crunchy Taps",     "High-frequency ASMR tapping.",         isIAP = true,  swatchBase = c("#5D4037"), swatchHighlight = c("#D7CCC8")),
        SlimeSound("sound_004", "Ocean Waves",      "Steady rhythm of the tide.",            isIAP = true,  swatchBase = c("#006064"), swatchHighlight = c("#80DEEA")),
        SlimeSound("sound_005", "Cat Purr",         "A soothing, deep rumble.",              isIAP = true,  swatchBase = c("#6A1B9A"), swatchHighlight = c("#CE93D8")),
        SlimeSound("sound_006", "Cozy Fire",        "Crackling wood in a fireplace.",        isIAP = true,  swatchBase = c("#BF360C"), swatchHighlight = c("#FFAB91")),
        SlimeSound("sound_007", "Magic Chimes",     "Ethereal, light bells.",               isIAP = true,  swatchBase = c("#4A148C"), swatchHighlight = c("#EA80FC")),
        SlimeSound("sound_008", "Page Flips",       "Crispy paper sounds.",                 isIAP = true,  swatchBase = c("#827717"), swatchHighlight = c("#FFF176")),
        SlimeSound("sound_009", "Snow Crunch",      "Walking on fresh powder.",             isIAP = true,  swatchBase = c("#37474F"), swatchHighlight = c("#CFD8DC")),
        SlimeSound("sound_010", "Keyboard Clicks",  "Mechanical typing satisfaction.",      isIAP = true,  swatchBase = c("#1A237E"), swatchHighlight = c("#9FA8DA")),
        SlimeSound("sound_011", "Ticking Clock",    "Steady, hypnotic tempo.",              isIAP = true,  swatchBase = c("#263238"), swatchHighlight = c("#90A4AE")),
        SlimeSound("sound_012", "Bubble Wrap",      "Infinite popping satisfaction.",       isIAP = true,  swatchBase = c("#00838F"), swatchHighlight = c("#B2EBF2")),
        SlimeSound("sound_013", "White Noise",      "Classic static for focus.",            isIAP = true,  swatchBase = c("#424242"), swatchHighlight = c("#BDBDBD")),
        SlimeSound("sound_014", "Deep Hum",         "Low-frequency vibration.",             isIAP = true,  swatchBase = c("#1B0000"), swatchHighlight = c("#6D4C41")),
        SlimeSound("sound_015", "Rainforest",       "Tropical birds and rain.",             isIAP = true,  swatchBase = c("#1B5E20"), swatchHighlight = c("#69F0AE")),
        SlimeSound("sound_016", "Stream Flow",      "Babbling brook water.",                isIAP = true,  swatchBase = c("#0277BD"), swatchHighlight = c("#81D4FA")),
        SlimeSound("sound_017", "Zen Garden",       "Sand raking and calm.",                isIAP = true,  swatchBase = c("#BF8B00"), swatchHighlight = c("#FFE082")),
        SlimeSound("sound_018", "Wind Chimes",      "Breezy bamboo sounds.",                isIAP = true,  swatchBase = c("#00695C"), swatchHighlight = c("#80CBC4")),
        SlimeSound("sound_019", "Vinyl Static",     "Vintage record player crackle.",       isIAP = true,  swatchBase = c("#212121"), swatchHighlight = c("#9E9E9E")),
        SlimeSound("sound_020", "Bowl Sing",        "Tibetan singing bowl resonance.",      isIAP = true,  swatchBase = c("#4A148C"), swatchHighlight = c("#EA80FC")),
        SlimeSound("sound_021", "Rain on Tin",      "Loud, metallic rain taps.",            isIAP = true,  swatchBase = c("#0D47A1"), swatchHighlight = c("#64B5F6")),
        SlimeSound("sound_022", "Library Ambience", "Quiet echoes and whispers.",           isIAP = true,  swatchBase = c("#3E2723"), swatchHighlight = c("#BCAAA4")),
        SlimeSound("sound_023", "Coffee Shop",      "Muted chatter and cups.",              isIAP = true,  swatchBase = c("#4E342E"), swatchHighlight = c("#FFAB91")),
        SlimeSound("sound_024", "Crickets",         "Warm summer night vibes.",             isIAP = true,  swatchBase = c("#33691E"), swatchHighlight = c("#CCFF90")),
        SlimeSound("sound_025", "Space Drone",      "Cinematic sci-fi void.",               isIAP = true,  swatchBase = c("#0D0D2B"), swatchHighlight = c("#7C4DFF")),
        SlimeSound("sound_026", "Submarine",        "Sonar pings and deep water.",          isIAP = true,  swatchBase = c("#01579B"), swatchHighlight = c("#4FC3F7")),
        SlimeSound("sound_027", "Train Tracks",     "Rhythmic clack of the rail.",          isIAP = true,  swatchBase = c("#37474F"), swatchHighlight = c("#B0BEC5")),
        SlimeSound("sound_028", "Thunder",          "Distant, rolling storms.",             isIAP = true,  swatchBase = c("#212121"), swatchHighlight = c("#7C4DFF")),
        SlimeSound("sound_029", "Grass Rustle",     "Walking through a meadow.",            isIAP = true,  swatchBase = c("#558B2F"), swatchHighlight = c("#DCEDC8")),
        SlimeSound("sound_030", "Sand Pour",        "Flowing granular texture.",            isIAP = true,  swatchBase = c("#E65100"), swatchHighlight = c("#FFCC80")),
        SlimeSound("sound_031", "Plastic Crinkle",  "Snack bag ASMR.",                      isIAP = true,  swatchBase = c("#00796B"), swatchHighlight = c("#B2DFDB")),
        SlimeSound("sound_032", "Soap Carving",     "Crisp shaving sounds.",                isIAP = true,  swatchBase = c("#006064"), swatchHighlight = c("#E0F7FA")),
        SlimeSound("sound_033", "Pencil Sketch",    "Lead on textured paper.",              isIAP = true,  swatchBase = c("#616161"), swatchHighlight = c("#F5F5F5")),
        SlimeSound("sound_034", "Ice Clink",        "Glass and frozen cubes.",              isIAP = true,  swatchBase = c("#0097A7"), swatchHighlight = c("#B2EBF2")),
        SlimeSound("sound_035", "Fan Whir",         "Steady electric hum.",                 isIAP = true,  swatchBase = c("#455A64"), swatchHighlight = c("#B0BEC5")),
        SlimeSound("sound_036", "Beating Heart",    "Vital, steady pulse.",                 isIAP = true,  swatchBase = c("#B71C1C"), swatchHighlight = c("#EF9A9A")),
        SlimeSound("sound_037", "Boiling Water",    "Soft bubbling texture.",               isIAP = true,  swatchBase = c("#006064"), swatchHighlight = c("#E0F7FA")),
        SlimeSound("sound_038", "Windy Canyon",     "Howling mountain air.",                isIAP = true,  swatchBase = c("#4E342E"), swatchHighlight = c("#FFA726")),
        SlimeSound("sound_039", "Scissor Snip",     "Sharp metallic cuts.",                 isIAP = true,  swatchBase = c("#546E7A"), swatchHighlight = c("#CFD8DC")),
        SlimeSound("sound_040", "Brush Strokes",    "Soft canvas painting.",                isIAP = true,  swatchBase = c("#AD1457"), swatchHighlight = c("#F48FB1")),
        SlimeSound("sound_041", "Bee Buzz",         "Distant summer garden.",               isIAP = true,  swatchBase = c("#F57F17"), swatchHighlight = c("#FFF9C4")),
        SlimeSound("sound_042", "Frogs",            "Evening swamp chorus.",                isIAP = true,  swatchBase = c("#2E7D32"), swatchHighlight = c("#A5D6A7")),
        SlimeSound("sound_043", "Dripping Tap",     "Echoing water drops.",                 isIAP = true,  swatchBase = c("#006064"), swatchHighlight = c("#B2EBF2")),
        SlimeSound("sound_044", "Paper Rip",        "Clean, fibrous tearing.",              isIAP = true,  swatchBase = c("#8D6E63"), swatchHighlight = c("#D7CCC8")),
        SlimeSound("sound_045", "Wooden Blocks",    "Solid wood clicks.",                   isIAP = true,  swatchBase = c("#5D4037"), swatchHighlight = c("#BCAAA4")),
        SlimeSound("sound_046", "Clock Tower",      "Distant, heavy bells.",                isIAP = true,  swatchBase = c("#263238"), swatchHighlight = c("#78909C")),
        SlimeSound("sound_047", "Dry Leaves",       "Autumn forest walk.",                  isIAP = true,  swatchBase = c("#E65100"), swatchHighlight = c("#FFB74D")),
        SlimeSound("sound_048", "Marble Roll",      "Glass on hard wood.",                  isIAP = true,  swatchBase = c("#546E7A"), swatchHighlight = c("#ECEFF1")),
        SlimeSound("sound_049", "Whale Song",       "Deep oceanic moans.",                  isIAP = true,  swatchBase = c("#004D7A"), swatchHighlight = c("#00B4D8")),
        SlimeSound("sound_050", "Supernova",        "Ethereal cosmic explosion.",           isIAP = true,  swatchBase = c("#4A148C"), swatchHighlight = c("#FF6D00"))
    )

    fun getSoundById(id: String): SlimeSound = sounds.find { it.id == id } ?: sounds[0]
}
