package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.Resource.Companion.isImage
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.darkyen.resourcepacker.util.preBlendImage
import com.google.common.io.Files

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
    val PreBlendRegex = Regex("#$ColorRegexGroup")

    /** Do your work here.
     * @return whether the operation did something or not */
    override fun operate(file: ResourceFile): Boolean {
        if (file.isImage()) {
            file.flags.matchFirst(PreBlendRegex) { (color) ->
                val output = newFile(file)
                Files.copy(file.file, output)
                preBlendImage(output, parseHexColor(color).toAwt(), ninepatch = file.flags.contains("9"))
                file.file = output
                return true
            }
        }

        return false
    }
}
