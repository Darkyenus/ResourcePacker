package com.darkyen.resourcepacker.tasks

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.IntSet
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.darkyen.resourcepacker.isFont
import com.darkyen.resourcepacker.tasks.font.STBFontPacker
import com.darkyen.resourcepacker.util.*
import com.darkyen.resourcepacker.util.FreeTypePacker.FreeTypeFontParameter
import com.esotericsoftware.minlog.Log
import com.google.common.io.Files

/**
 * Rasterizes .ttf fonts.
 * Font must have .N. flag where N is font size in pixels.
 *
 * Flags:
 * ```
 * <N> - size - mandatory
 *
 * <S>-<E> - adds all codepoints from S to E, inclusive. Missing glyphs are not added.
 *
 * bg#RRGGBBAA - background color, default is transparent
 *
 * fg#RRGGBBAA - foreground color, default is white
 *
 * outline <W> RRGGBBAA [straight] - add outline of width W and specified color, add "straight" for sharp/mitered edges
 * ```
 *
 * When no glyphs are specified, all available glyphs are added.
 *
 * @author Darkyen
 */
object CreateFontsTask : Task() {

    /* Example:
     * 14          => Size 14
     */
    private val SizeRegex = Regex("(\\d+)")

    /* Example:
     * 56-67     => Glyphs 56 to 67 (decimal, inclusive) should be added
     */
    private val GlyphRangeRegex = Regex("(\\d+)-(\\d+)")

    /* Example:
     * outline 4 FF0000 miter   => Will create solid red 4px outline with miter joins
     */
    private val OutlineRegex = Regex("outline (\\d+) $ColorRegexGroup ?(\\w+)?")
    //outline (\\d+) ([0-9A-Fa-f]{3,8}) ?(\\w+)?

    /** Matches bg#RRGGBBAA colors for background. Default is Transparent. */
    private val BGRegex = Regex("bg#$ColorRegexGroup")
    /** Matches bg#RRGGBBAA colors for foreground (color of font). Default is White. */
    private val FGRegex = Regex("fg#$ColorRegexGroup")

    fun packFreeTypeFont(file: ResourceFile, size:Int) {
        val parameter = FreeTypeFontParameter()
        parameter.fontName = file.name
        parameter.size = size

        for (param in file.flags) {
            val (width, hexColor, optJoin) = OutlineRegex.matchEntire(param) ?: continue
            parameter.borderWidth = width.toFloat()
            parameter.borderColor = parseHexColor(hexColor)
            parameter.borderStraight = "straight".equals(optJoin, ignoreCase = true)
            break
        }

        for (param in file.flags) {
            val (hexColor) = FGRegex.matchEntire(param) ?: continue
            parameter.color = parseHexColor(hexColor)
            break
        }

        val glyphsToAdd = IntSet()
        for (p in file.flags) {
            val (start, end) = GlyphRangeRegex.matchEntire(p) ?: continue

            val from = start.toInt()
            val to = end.toInt()
            for (i in from..to) {
                glyphsToAdd.add(i)
            }
            Log.debug(Name, "Added glyphs from $from (${java.lang.Character.toChars(from).contentToString()}) to $to (${java.lang.Character.toChars(to).contentToString()}).")
        }
        if (glyphsToAdd.size != 0) {
            parameter.codePoints = glyphsToAdd
        }

        val outputFolder = newFolder()
        val packedFiles = FreeTypePacker.pack(file.file, outputFolder, parameter)

        val pageCount = packedFiles.size - 1
        if (pageCount <= 0) {
            Log.warn(Name, "Font didn't render on any pages. " + file)
        } else if (pageCount > 1) {
            Log.warn(Name, "Font did render on more than one page. This may case problems when loading for UI skin. $pageCount $file")
        }
        Log.info(Name, "Font created. " + file)
        file.parent.removeChild(file)

        var bgColor: Color? = null
        for (param in file.flags) {
            bgColor = parseHexColor(BGRegex.matchEntire(param)?.groupValues?.get(1) ?: continue)
            Log.debug(Name, "Background color for font set. " + file)
        }

        for (generatedJavaFile in packedFiles) {
            if (Files.getFileExtension(generatedJavaFile.name).equals("png", ignoreCase = true)) {
                clampImage(generatedJavaFile)
                if (bgColor != null) {
                    val jColor = java.awt.Color(bgColor.r, bgColor.g, bgColor.b, bgColor.a)
                    preBlendImage(generatedJavaFile, jColor)
                }
            }
            val f = file.parent.addChild(generatedJavaFile)
            Log.debug(Name, "Font file added. " + f)
        }
    }

    fun packStbFont(file:ResourceFile, size: Int) {
        val resultFiles = STBFontPacker.packFont(file.file, file.name, newFolder(), size, true)

        for (resultFile in resultFiles) {
            file.parent.addChild(resultFile)
        }
        file.removeFromParent()
        Log.info("StbFont from $file created (${resultFiles.size} result files)")
    }

    override fun operate(file: ResourceFile): Boolean {
        if (file.isFont()) {
            var size: Int = -1

            for (flag in file.flags) {
                val sizeMatch = SizeRegex.matchEntire(flag)
                if (sizeMatch != null) {
                    size = sizeMatch.groupValues[1].toInt()
                    continue
                }
            }

            if (size < 0) {
                Log.debug(Name, "Not rasterizing font, size not specified." + file)
            } else if (size == 0) {
                Log.error(Name, "Size must be bigger than 0. " + file)
            } else {
                if (file.flags.contains("stbfont")) {
                    packStbFont(file, size)
                } else {
                    packFreeTypeFont(file, size)
                }
            }
            return true
        }

        return false
    }
}
