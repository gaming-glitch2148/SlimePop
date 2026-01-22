package com.slimepop.asmr

object ContentNames {
    val skinNames: List<String> = listOf(
        "Velvet Mint","Rose Quartz","Midnight Ink","Honey Amber","Ocean Glass",
        "Lilac Haze","Neon Coral","Arctic Pearl","Cocoa Silk","Sunset Sherbet",
        "Aurora Bloom","Sapphire Mist","Matcha Foam","Cherry Blossom","Copper Glow",
        "Blue Lagoon","Grape Jelly","Mango Cream","Silver Lining","Opal Dream",
        "Frosted Plum","Citrus Zest","Rainy Day","Lavender Milk","Sea Salt",
        "Pistachio","Strawberry Milk","Cosmic Violet","Lemon Fizz","Rosewood",
        "Tidepool","Sky Candy","Mocha Cloud","Watermelon Pop","Dragonfruit",
        "Peach Sorbet","Galaxy Teal","Berry Jam","Pearl Pink","Canyon Clay",
        "Glacier Blue","Jade Satin","Sunrise Gold","Ink Wash","Coconut Cream",
        "Sakura Glow","Stormy Slate","Mint Chip","Plum Velvet","Electric Lime"
    )

    val soundNames: List<String> = listOf(
        "Soft Rain","Ocean Shore","Singing Bowl","Forest Night","Cozy Fireplace",
        "Gentle Wind","White Noise","Pink Noise","Brown Noise","Distant Thunder",
        "River Stream","Morning Birds","Crickets","City Rain","Tea Kettle",
        "Vinyl Crackle","Pencil Scribble","Page Turns","Soft Bubbles","Deep Hum",
        "Calm Chimes","Temple Bells","Snowfall","Underwater","Desert Breeze",
        "Tropical Night","Space Ambience","Lo-Fi Room","Tapping Wood","Tapping Glass",
        "Soft Keyboard","Quiet Cafe","Airplane Cabin","Train Ride","Fan Whisper",
        "Water Drops","Ice Clinks","Wind Chimes","Soft Drum","Handpan Drift",
        "Dream Pad","Night Waves","Rain on Roof","Campfire","Ocean Storm",
        "Jungle Rain","Autumn Leaves","Distant Choir","Soft Static","Silk Brush"
    )

    fun skinNameFor(id: String): String {
        val n = id.removePrefix("skin_").toIntOrNull() ?: 1
        return skinNames[(n - 1).coerceIn(0, 49)]
    }

    fun soundNameFor(id: String): String {
        val n = id.removePrefix("sound_").toIntOrNull() ?: 1
        return soundNames[(n - 1).coerceIn(0, 49)]
    }
}