package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.Resource.Companion.isImage
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.darkyen.resourcepacker.util.preBlendImage
import com.google.common.io.Files
import java.awt.Color

/**
 * Renders image onto background of given color.
 *
 * ```
 *   Input flags:
 *
 *   .#RRGGBB.
 *
 *   CC colors are in hexa
 * ```
 *
 * @example
 * `.#FFFFFF.` would put a white background on the image
 *
 * @author Darkyen
 */
object PreBlendTask : Task() {

    /**
     * Matches: #RRGGBB
     * Where RR (GG and BB) are hexadecimal digits.
     * Example:
     * #FF0056
     * to capture groups RR GG BB
     */
    val PreBlendRegex = Regex("#([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])")

    /** Do your work here.
     * @return whether the operation did something or not */
    override fun operate(file: ResourceFile): Boolean {
        if (file.isImage()) {
            file.flags.matchFirst(PreBlendRegex) { (r, g, b) ->
                val color = Color(Integer.parseInt(r, 16), Integer.parseInt(g, 16), Integer.parseInt(b, 16))
                val output = newFile(file)
                Files.copy(file.file, output)
                preBlendImage(output, color, ninepatch = file.flags.contains("9"))
                file.file = output
                return true
            }
        }

        return false
    }
}
