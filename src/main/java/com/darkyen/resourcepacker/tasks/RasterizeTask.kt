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
            val bitmap = image.image(image.width * scale, image.height * scale)
            val resultFile = newFileNamed(file, file.name + (if (scale == 1) "" else "@" + scale + "x"), "png")
            bitmap.saveToFile(resultFile, "png")

            file.parent.addChild(resultFile)
            Log.info(Name, "$file rasterized @ ${scale}x")
        }

        return true
    }
}
