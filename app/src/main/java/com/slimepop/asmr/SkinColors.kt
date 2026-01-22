package com.slimepop.asmr

import android.graphics.Color

object SkinColors {
    data class ColorPair(val base: Int, val hi: Int)

    private val colors = listOf(
        "#70DB93" to "#B4EEB4", // 1 Velvet Mint
        "#F7CAC9" to "#FFD1DC", // 2 Rose Quartz
        "#000033" to "#191970", // 3 Midnight Ink
        "#FFBF00" to "#FFD700", // 4 Honey Amber
        "#48D1CC" to "#E0FFFF", // 5 Ocean Glass
        "#8E4585" to "#D8BFD8", // 6 Lilac Haze
        "#FF4040" to "#FF7F50", // 7 Neon Coral
        "#DEE4E7" to "#F0F8FF", // 8 Arctic Pearl
        "#3E2723" to "#A1887F", // 9 Cocoa Silk
        "#FF4E50" to "#F9D423", // 10 Sunset Sherbet
        "#00D2FF" to "#92FE9D", // 11 Aurora Bloom
        "#0066B2" to "#87CEFA", // 12 Sapphire Mist
        "#96A265" to "#C1CDC1", // 13 Matcha Foam
        "#FFB7C5" to "#FFF0F5", // 14 Cherry Blossom
        "#B87333" to "#DAA520", // 15 Copper Glow
        "#007BA7" to "#AFEEEE", // 16 Blue Lagoon
        "#6F2DA8" to "#E6E6FA", // 17 Grape Jelly
        "#FF8243" to "#FFE5B4", // 18 Mango Cream
        "#C0C0C0" to "#E8E8E8", // 19 Silver Lining
        "#A8E6CF" to "#FFD3B6", // 20 Opal Dream
        "#8E4585" to "#DDA0DD", // 21 Frosted Plum
        "#FFA500" to "#FFFF00", // 22 Citrus Zest
        "#708090" to "#B0C4DE", // 23 Rainy Day
        "#967BB6" to "#E6E6FA", // 24 Lavender Milk
        "#F5F5DC" to "#F0FFF0", // 25 Sea Salt
        "#93C572" to "#E9FFDB", // 26 Pistachio
        "#FC8EAC" to "#FFF0F5", // 27 Strawberry Milk
        "#240046" to "#BFD7ED", // 28 Cosmic Violet
        "#FFF44F" to "#FFFFF0", // 29 Lemon Fizz
        "#65000B" to "#CD5C5C", // 30 Rosewood
        "#2F4F4F" to "#20B2AA", // 31 Tidepool
        "#87CEEB" to "#FFB6C1", // 32 Sky Candy
        "#4B3621" to "#D2B48C", // 33 Mocha Cloud
        "#E34234" to "#90EE90", // 34 Watermelon Pop
        "#C71585" to "#FF69B4", // 35 Dragonfruit
        "#FFDAB9" to "#FFE4E1", // 36 Peach Sorbet
        "#008080" to "#E6E6FA", // 37 Galaxy Teal
        "#7A0823" to "#FF4D00", // 38 Berry Jam
        "#FAD6A5" to "#FFFDD0", // 39 Pearl Pink
        "#A0522D" to "#DEB887", // 40 Canyon Clay
        "#00FFFF" to "#F0FFFF", // 41 Glacier Blue
        "#00A86B" to "#98FF98", // 42 Jade Satin
        "#FFD700" to "#FFFACD", // 43 Sunrise Gold
        "#36454F" to "#DCDCDC", // 44 Ink Wash
        "#FFFDD0" to "#FFFFFF", // 45 Coconut Cream
        "#FF69B4" to "#FFF0F5", // 46 Sakura Glow
        "#4A4E69" to "#9A8C98", // 47 Stormy Slate
        "#3EB489" to "#E0FFF0", // 48 Mint Chip
        "#673147" to "#DDA0DD", // 49 Plum Velvet
        "#CCFF00" to "#7FFF00"  // 50 Electric Lime
    )

    fun get(index0: Int): ColorPair {
        val i = index0.coerceIn(0, 49)
        val p = colors[i]
        return ColorPair(Color.parseColor(p.first), Color.parseColor(p.second))
    }
}
