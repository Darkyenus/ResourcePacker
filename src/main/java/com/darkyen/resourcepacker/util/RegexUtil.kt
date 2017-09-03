@file:Suppress("NOTHING_TO_INLINE")

package com.darkyen.resourcepacker.util

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.esotericsoftware.minlog.Log

/**
 *
 */
internal operator inline fun MatchResult.component1(): String = this.groupValues[1]

internal operator inline fun MatchResult.component2(): String = this.groupValues[2]
internal operator inline fun MatchResult.component3(): String = this.groupValues[3]
internal operator inline fun MatchResult.component4(): String = this.groupValues[4]
internal operator inline fun MatchResult.component5(): String = this.groupValues[5]
internal operator inline fun MatchResult.component6(): String = this.groupValues[6]
internal operator inline fun MatchResult.component7(): String = this.groupValues[7]
internal operator inline fun MatchResult.component8(): String = this.groupValues[8]
internal operator inline fun MatchResult.component9(): String = this.groupValues[9]
internal operator inline fun MatchResult.component10(): String = this.groupValues[10]

internal inline fun <T> Iterable<String>.matchFirst(r: Regex, warnIfMore:Boolean = true, collector: (MatchResult) -> T): T? {
    var result:T? = null
    var hasResult = false
    for (string in this) {
        val match = r.matchEntire(string) ?: continue
        if (hasResult) {
            Log.warn("matchFirst", "Multiple matches for '${r.pattern}' pattern")
            continue
        }
        result = collector(match)
        if (warnIfMore) {
            hasResult = true
        } else {
            return result
        }
    }
    return result
}

internal inline fun Iterable<String>.matchAll(r: Regex, collector: (MatchResult) -> Unit) {
    for (string in this) {
        val match = r.matchEntire(string) ?: continue
        collector(match)
    }
}

/**
 * Regex group used for matching colors in hex.
 * Matches 1 to 8 hex numbers, intuitively mapping them to these interpretations:
 * (G=gray-scale, R=red, G=green, B=blue, A=alpha)
 *
 * 1) G
 * 2) GA
 * 3) RGB
 * 4) RGBA
 * 5) RGBAA
 * 6) RRGGBB
 * 7) RRGGBBA
 * 8) RRGGBBAA
 *
 * @see parseHexColor
 */
internal val ColorRegexGroup = "([0-9A-Fa-f]{1,8})"

/**
 * Parse color matched by [ColorRegexGroup].
 */
internal fun parseHexColor(hex: String): Color {
    fun Int.c(): Float = MathUtils.clamp(this, 0, 255).toFloat() / 255f

    return when (hex.length) {
        1 -> {//Gray
            val gray = (Integer.parseInt(hex, 16) * 0xF).c()
            Color(gray, gray, gray, 1f)
        }
        2 -> { //Gray with alpha
            val gray = (Integer.parseInt(hex.substring(0, 1), 16) * 0xF).c()
            val alpha = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF).c()
            Color(gray, gray, gray, alpha)

        }
        3 -> {//RGB
            val r = (Integer.parseInt(hex.substring(0, 1), 16) * 0xF).c()
            val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF).c()
            val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF).c()
            Color(r, g, b, 1f)

        }
        4 -> {//RGBA
            val r = (Integer.parseInt(hex.substring(0, 1), 16) * 0xF).c()
            val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF).c()
            val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF).c()
            val a = (Integer.parseInt(hex.substring(3, 4), 16) * 0xF).c()
            Color(r, g, b, a)

        }
        5 -> {//RGBAA
            val r = (Integer.parseInt(hex.substring(0, 1), 16) * 0xF).c()
            val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF).c()
            val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF).c()
            val a = Integer.parseInt(hex.substring(3, 5), 16).c()
            Color(r, g, b, a)

        }
        6 -> {//RRGGBB
            val r = Integer.parseInt(hex.substring(0, 2), 16).c()
            val g = Integer.parseInt(hex.substring(2, 4), 16).c()
            val b = Integer.parseInt(hex.substring(4, 6), 16).c()
            Color(r, g, b, 1f)

        }
        7 -> {//RRGGBBA
            val r = Integer.parseInt(hex.substring(0, 2), 16).c()
            val g = Integer.parseInt(hex.substring(2, 4), 16).c()
            val b = Integer.parseInt(hex.substring(4, 6), 16).c()
            val a = (Integer.parseInt(hex.substring(6, 7), 16) * 0xF).c()
            Color(r, g, b, a)

        }
        8 -> {//RRGGBBAA
            val r = Integer.parseInt(hex.substring(0, 2), 16).c()
            val g = Integer.parseInt(hex.substring(2, 4), 16).c()
            val b = Integer.parseInt(hex.substring(4, 6), 16).c()
            val a = Integer.parseInt(hex.substring(6, 8), 16).c()
            Color(r, g, b, a)

        }
        else -> error("Invalid color: $hex")
    }
}

internal fun Color.toAwt():java.awt.Color {
    return java.awt.Color(this.r, this.g, this.b, this.a)
}