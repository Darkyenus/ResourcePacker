@file:Suppress("NOTHING_TO_INLINE")

package com.darkyen.resourcepacker.tasks

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

internal inline fun <T> Iterable<String>.matchFirst(r: Regex, collector: (MatchResult) -> T): T? {
    for (string in this) {
        return collector(r.matchEntire(string) ?: continue)
    }
    return null
}

internal inline fun Iterable<String>.matchAll(r: Regex, collector: (MatchResult) -> Unit) {
    for (string in this) {
        collector(r.matchEntire(string) ?: continue)
    }
}