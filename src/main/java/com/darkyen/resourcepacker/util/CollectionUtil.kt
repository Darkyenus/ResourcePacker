package com.darkyen.resourcepacker.util

import com.badlogic.gdx.utils.IntArray

/**
 *
 */
inline fun IntArray.forEach(operation:(Int)->Unit) {
    val size = this.size
    val items = this.items
    var i = 0
    while (i < size) {
        operation(items[i])
        i += 1
    }
}