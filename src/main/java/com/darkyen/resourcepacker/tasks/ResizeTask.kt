package com.darkyen.resourcepacker.tasks

import java.awt.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import com.badlogic.gdx.math.MathUtils
import com.darkyen.resourcepacker.Resource.Companion.isImage
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.SettingKey
import com.darkyen.resourcepacker.Task
import com.esotericsoftware.minlog.Log

/**
 * Resizes image by given arguments
 *
 * Flags:
 *
 * .w<W>h<H>. - Image will be W tile-sizes wide and H tile-sizes tall
 *
 * .<W>x<H>. - Image will be W pixels wide and H pixels tall
 *
 * .dont-upsample. - Image won't be resized if one of dimensions is larger than original
 *
 * Settings:
 * TileSize - Size of tile used by ResizeTask's w<W>h<H> flag pattern
 *
 * @author Darkyen
 */
object ResizeTask : Task() {

    /**
    Examples:
    w15h1    => Image will be 15 tile-sizes wide and 1 tile size tall
    w1,5h0,5 => Image will be 1.5 tile-sizes wide and 0.5 tile size tall
     */
    val TileSizePattern = Regex("""w(\d+(?:,\d+)?)h(\d+(?:,\d+)?)""")

    /**
    Example:
    450x789   => Image will be 450 pixels wide and 789 pixels tall
     */
    val PixelSizePattern = Regex("""(\d+)x(\d+)""")

    val TileSize: SettingKey<Int> = SettingKey("TileSize", 128, "Size of tile used by ResizeTask's w<W>h<H> flag pattern")

    fun tileFraction(input: String): Int {
        return Math.round(input.replace(',', '.').toFloat() * TileSize.get())
    }

    override fun operate(file: ResourceFile): Boolean {
        if (file.isImage()) {
            for (flag in file.flags) {
                val tileMatch = TileSizePattern.matchEntire(flag)
                val pixelMatch = PixelSizePattern.matchEntire(flag)
                val width:Int
                val height:Int
                if (tileMatch != null) {
                    width = tileFraction(tileMatch.groupValues[1])
                    height = tileFraction(tileMatch.groupValues[2])
                } else if (pixelMatch != null) {
                    width = pixelMatch.groupValues[1].toInt()
                    height = pixelMatch.groupValues[2].toInt()
                } else continue

                val desiredRatio = width.toFloat() / height

                val rawImage = ImageIO.read(file.file)
                if (rawImage == null) {
                    Log.error(Name, "File does not exist! This shouldn't happen. (" + file.file.canonicalPath + ") " + file)
                }
                val currentRatio = rawImage.width.toFloat() / rawImage.height
                if (!MathUtils.isEqual(desiredRatio, currentRatio)) {
                    Log.warn(Name, "Desired ratio and current ratio of ${file.file.absolutePath} differ (Desired: $desiredRatio Current: $currentRatio)")
                }
                if (!(width > rawImage.width || height > rawImage.height) || !file.flags.contains("dont-upsample")) {
                    val resizedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                    val g = resizedImage.createGraphics()
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g.drawImage(rawImage, 0, 0, width, height, null)

                    val outputFile = newFile(file)
                    ImageIO.write(resizedImage, "PNG", outputFile)
                    g.dispose()
                    file.file = outputFile
                    Log.info(Name, "Image resized. " + file)
                }
                return true
            }
        }

        return false
    }
}
