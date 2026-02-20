package com.slimepop.asmr

data class SlimeSound(
    val id: String,
    val name: String,
    val description: String,
    val isIAP: Boolean = false,
    val coinPrice: Int = 0
)

object SoundCatalog {
    val sounds = listOf(
        SlimeSound("sound_001", "Soft Rain", "Gentle rain tapping on a window.", isIAP = false, coinPrice = 0),
        SlimeSound("sound_002", "Forest Whispers", "Calm wind and rustling leaves.", isIAP = true),
        SlimeSound("sound_003", "Crunchy Taps", "High-frequency ASMR tapping.", isIAP = true),
        SlimeSound("sound_004", "Ocean Waves", "Steady rhythm of the tide.", isIAP = true),
        SlimeSound("sound_005", "Cat Purr", "A soothing, deep rumble.", isIAP = true),
        SlimeSound("sound_006", "Cozy Fire", "Crackling wood in a fireplace.", isIAP = true),
        SlimeSound("sound_007", "Magic Chimes", "Ethereal, light bells.", isIAP = true),
        SlimeSound("sound_008", "Page Flips", "Crispy paper sounds.", isIAP = true),
        SlimeSound("sound_009", "Snow Crunch", "Walking on fresh powder.", isIAP = true),
        SlimeSound("sound_010", "Keyboard Clicks", "Mechanical typing satisfaction.", isIAP = true),
        SlimeSound("sound_011", "Ticking Clock", "Steady, hypnotic tempo.", isIAP = true),
        SlimeSound("sound_012", "Bubble Wrap", "Infinite popping satisfaction.", isIAP = true),
        SlimeSound("sound_013", "White Noise", "Classic static for focus.", isIAP = true),
        SlimeSound("sound_014", "Deep Hum", "Low-frequency vibration.", isIAP = true),
        SlimeSound("sound_015", "Rainforest", "Tropical birds and rain.", isIAP = true),
        SlimeSound("sound_016", "Stream Flow", "Babbling brook water.", isIAP = true),
        SlimeSound("sound_017", "Zen Garden", "Sand raking and calm.", isIAP = true),
        SlimeSound("sound_018", "Wind Chimes", "Breezy bamboo sounds.", isIAP = true),
        SlimeSound("sound_019", "Vinyl Static", "Vintage record player crackle.", isIAP = true),
        SlimeSound("sound_020", "Bowl Sing", "Tibetan singing bowl resonance.", isIAP = true),
        SlimeSound("sound_021", "Rain on Tin", "Loud, metallic rain taps.", isIAP = true),
        SlimeSound("sound_022", "Library Ambience", "Quiet echoes and whispers.", isIAP = true),
        SlimeSound("sound_023", "Coffee Shop", "Muted chatter and cups.", isIAP = true),
        SlimeSound("sound_024", "Crickets", "Warm summer night vibes.", isIAP = true),
        SlimeSound("sound_025", "Space Drone", "Cinematic sci-fi void.", isIAP = true),
        SlimeSound("sound_026", "Submarine", "Sonar pings and deep water.", isIAP = true),
        SlimeSound("sound_027", "Train Tracks", "Rhythmic clack of the rail.", isIAP = true),
        SlimeSound("sound_028", "Thunder", "Distant, rolling storms.", isIAP = true),
        SlimeSound("sound_029", "Grass Rustle", "Walking through a meadow.", isIAP = true),
        SlimeSound("sound_030", "Sand Pour", "Flowing granular texture.", isIAP = true),
        SlimeSound("sound_031", "Plastic Crinkle", "Snack bag ASMR.", isIAP = true),
        SlimeSound("sound_032", "Soap Carving", "Crisp shaving sounds.", isIAP = true),
        SlimeSound("sound_033", "Pencil Sketch", "Lead on textured paper.", isIAP = true),
        SlimeSound("sound_034", "Ice Clink", "Glass and frozen cubes.", isIAP = true),
        SlimeSound("sound_035", "Fan Whir", "Steady electric hum.", isIAP = true),
        SlimeSound("sound_036", "Beating Heart", "Vital, steady pulse.", isIAP = true),
        SlimeSound("sound_037", "Boiling Water", "Soft bubbling texture.", isIAP = true),
        SlimeSound("sound_038", "Windy Canyon", "Howling mountain air.", isIAP = true),
        SlimeSound("sound_039", "Scissor Snip", "Sharp metallic cuts.", isIAP = true),
        SlimeSound("sound_040", "Brush Strokes", "Soft canvas painting.", isIAP = true),
        SlimeSound("sound_041", "Bee Buzz", "Distant summer garden.", isIAP = true),
        SlimeSound("sound_042", "Frogs", "Evening swamp chorus.", isIAP = true),
        SlimeSound("sound_043", "Dripping Tap", "Echoing water drops.", isIAP = true),
        SlimeSound("sound_044", "Paper Rip", "Clean, fibrous tearing.", isIAP = true),
        SlimeSound("sound_045", "Wooden Blocks", "Solid wood clicks.", isIAP = true),
        SlimeSound("sound_046", "Clock Tower", "Distant, heavy bells.", isIAP = true),
        SlimeSound("sound_047", "Dry Leaves", "Autumn forest walk.", isIAP = true),
        SlimeSound("sound_048", "Marble Roll", "Glass on hard wood.", isIAP = true),
        SlimeSound("sound_049", "Whale Song", "Deep oceanic moans.", isIAP = true),
        SlimeSound("sound_050", "Supernova", "Ethereal cosmic explosion.", isIAP = true)
    )

    fun getSoundById(id: String): SlimeSound = sounds.find { it.id == id } ?: sounds[0]
}
