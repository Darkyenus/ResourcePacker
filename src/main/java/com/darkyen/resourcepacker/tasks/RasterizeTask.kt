package com.darkyen.resourcepacker.tasks

import com.badlogic.gdx.utils.IntArray
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.darkyen.resourcepacker.image.createImage
import com.darkyen.resourcepacker.image.saveToFile
import com.darkyen.resourcepacker.isImage
import com.darkyen.resourcepacker.util.component1
import com.darkyen.resourcepacker.util.forEach
import com.darkyen.resourcepacker.util.matchAll
import com.esotericsoftware.minlog.Log
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Rasterizes .svg files with .rasterize. (or .r. for short) flag using default size (in file) or one specified in flags.
 *
 * Flags:
 * ```
 *   .rasterize. (or .r.)
 *   Triggers rasterization
 *
 *   .scaled.
 *   Will check parent directories. If one of them contains flags in form @Nx, where N is a whole number,
 *   rasterizer will additionally generate png with dimensions*N for each such flag and save it into png with @Nx
 *   appended, unless it already exists. (In that case, no extra image is generated)
 * ```
 *
 * @author Darkyen
 */
object RasterizeTask : Task() {

    private val ScaledFactorSpecifierPattern = Regex("""@([1-9]+[0-9]*)x""")

    override fun operate(file: ResourceFile): Boolean {
        if (!file.isImage() || !(file.flags.contains("rasterize") || file.flags.contains("r"))) {
            return false
        }

        val scales = IntArray()
        scales.add(1)
        if (file.flags.contains("scaled")) {
            var parent = file.parent
            while (true) {
                parent.flags.matchAll(ScaledFactorSpecifierPattern) { (factorStr) ->
                    val factor = factorStr.toInt()
                    if (!scales.contains(factor)) {
                        scales.add(factor)
                    }
                }

                if (parent == parent.parent) {
                    break
                } else {
                    parent = parent.parent
                }
            }
        }

        val image = file.createImage()!!
        file.removeFromParent()

        scales.forEach { scale ->
            var bitmap = image.image(image.width * scale, image.height * scale)
            if (image.ninepatch) {
                // Add ninepatch data to the image
                val ninepatch = BufferedImage(bitmap.width + 2, bitmap.height + 2, BufferedImage.TYPE_INT_ARGB)
                val g = ninepatch.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)

                g.drawImage(bitmap, 1, 1, bitmap.width, bitmap.height, null)

                val splits = image.ninepatchSplits(bitmap.width, bitmap.height)!!
                g.color = Color.BLACK
                g.fillRect(1+splits[0], 0, bitmap.width - splits[1] - splits[0], 1)
                g.fillRect(0, 1 + splits[2], 1,bitmap.height - splits[3] - splits[2])

                val pads = image.ninepatchPads(bitmap.width, bitmap.height)
                if (pads != null) {
                    g.fillRect(1+pads[0], ninepatch.height - 1, bitmap.width - pads[1] - pads[0], 1)
                    g.fillRect(ninepatch.width - 1, 1 + pads[2], 1,bitmap.height - pads[3] - pads[2])
                }

                g.dispose()

                bitmap = ninepatch
            }

            val resultFile = newFileNamed(file, file.name + (if (scale == 1) "" else "@" + scale + "x"), "png")
            bitmap.saveToFile(resultFile, "png")

            file.parent.addChild(resultFile)
            Log.info(Name, "$file rasterized @ ${scale}x")
        }

        return true
    }
}
