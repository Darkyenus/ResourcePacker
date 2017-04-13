package com.darkyen.resourcepacker.tasks

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.IntSet
import com.darkyen.resourcepacker.Resource.Companion.isFont
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.darkyen.resourcepacker.util.FreeTypePacker
import com.esotericsoftware.minlog.Log
import com.google.common.io.Files
import com.darkyen.resourcepacker.util.FreeTypePacker.FreeTypeFontParameter
import com.darkyen.resourcepacker.util.clampImage
import com.darkyen.resourcepacker.util.preBlendImage

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

    private val HexColorCapture = "([0-9A-Fa-f]{1,8})"

    private fun Int.c():Float = MathUtils.clamp(this, 0, 255).toFloat() / 255f

    private fun parseHexColor(hex:String):Color {
        return when (hex.length) {
            1 -> {//Gray
                val gray = (Integer.parseInt(hex, 16) * 0xF).c()
                Color(gray, gray, gray, 1f)
            }
            2 -> { //Gray with alpha
                val gray = (Integer.parseInt(hex.substring(0,1), 16) * 0xF).c()
                val alpha = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF).c()
                Color(gray, gray, gray, alpha)

            }
            3 -> {//RGB
                val r = (Integer.parseInt(hex.substring(0,1), 16) * 0xF).c()
                val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF).c()
                val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF).c()
                Color(r, g, b, 1f)

            }
            4 -> {//RGBA
                val r = (Integer.parseInt(hex.substring(0,1), 16) * 0xF).c()
                val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF).c()
                val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF).c()
                val a = (Integer.parseInt(hex.substring(3, 4), 16) * 0xF).c()
                Color(r,g,b,a)

            }
            5 -> {//RGBAA
                val r = (Integer.parseInt(hex.substring(0,1), 16) * 0xF).c()
                val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF).c()
                val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF).c()
                val a = Integer.parseInt(hex.substring(3, 5), 16).c()
                Color(r,g,b,a)

            }
            6 -> {//RRGGBB
                val r = Integer.parseInt(hex.substring(0, 2), 16).c()
                val g = Integer.parseInt(hex.substring(2, 4), 16).c()
                val b = Integer.parseInt(hex.substring(4, 6), 16).c()
                Color(r,g,b,1f)

            }
            7 -> {//RRGGBBA
                val r = Integer.parseInt(hex.substring(0, 2), 16).c()
                val g = Integer.parseInt(hex.substring(2, 4), 16).c()
                val b = Integer.parseInt(hex.substring(4, 6), 16).c()
                val a = (Integer.parseInt(hex.substring(6, 7), 16) * 0xF).c()
                Color(r,g,b,a)

            }
            8 -> {//RRGGBBAA
                val r = Integer.parseInt(hex.substring(0, 2), 16).c()
                val g = Integer.parseInt(hex.substring(2, 4), 16).c()
                val b = Integer.parseInt(hex.substring(4, 6), 16).c()
                val a = Integer.parseInt(hex.substring(6, 8), 16).c()
                Color(r,g,b,a)

            }
            else -> error("Invalid color: $hex")
        }
    }

    /* Example:
     * 14          => Size 14
     */
    val SizeRegex = Regex("(\\d+)")

    /* Example:
     * 56-67     => Glyphs 56 to 67 should be added
     */
    val GlyphRangeRegex = Regex("(\\d+)-(\\d+)")

    /* Example:
     * outline 4 FF0000 miter   => Will create solid red 4px outline with miter joins
     */
    val OutlineRegex = Regex("outline (\\d+) $HexColorCapture ?(\\w+)?")
    //outline (\\d+) ([0-9A-Fa-f]{3,8}) ?(\\w+)?

    /** Matches bg#RRGGBBAA colors for background. Default is Transparent. */
    val BGRegex = Regex("bg#$HexColorCapture")
    /** Matches bg#RRGGBBAA colors for foreground (color of font). Default is White. */
    val FGRegex = Regex("fg#$HexColorCapture")



    override fun operate(file: ResourceFile): Boolean {
        if (file.isFont()) {
            val params = file.flags

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
                val parameter = FreeTypeFontParameter()
                parameter.fontName = file.name
                parameter.size = size

                for (param in params) {
                    val (width, hexColor, optJoin) = OutlineRegex.matchEntire(param) ?: continue
                    parameter.borderWidth = width.toFloat()
                    parameter.borderColor = parseHexColor(hexColor)
                    parameter.borderStraight = "straight".equals(optJoin, ignoreCase = true)
                    break
                }

                for (param in params) {
                    val (hexColor) = FGRegex.matchEntire(param) ?: continue
                    parameter.color = parseHexColor(hexColor)
                    break
                }

                val glyphsToAdd = IntSet()
                for (p in params) {
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

                var bgColor:Color? = null
                for (param in params) {
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
            return true
        }

        return false
    }
}
