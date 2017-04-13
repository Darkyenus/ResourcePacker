package com.darkyen.resourcepacker.tasks

import com.badlogic.gdx.utils.IntSet
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.esotericsoftware.minlog.Log
import org.apache.batik.transcoder.SVGAbstractTranscoder
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import org.apache.batik.transcoder.image.PNGTranscoder
import java.awt.Color
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Rasterizes .svg files with .rasterize. (or .r. for short) flag using default size (in file) or one specified in flags.
 *
 * Flags:
 * ```
 *   .rasterize. (or .r.)
 *   Triggers rasterization
 *
 *   .[W]x[H].
 *   W - width in pixels
 *   H - height in pixels
 *
 *   One of them can be left blank to infer the size by maintaining ratio.
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
    private val transcoder = PNGTranscoder()

    /**
    Example:
    450x789   => Image will be 450 pixels wide and 789 pixels tall
     */
    val PixelSizePattern = Regex("""(\d+)x(\d+)""")

    /**
    Example:
    450x   => Image will be 450 pixels wide and height will be inferred
     */
    val PixelWidthPattern = Regex("""(\d+)x""")

    /**
    Example:
    x789   => Image will be 789 pixels tall and width will be inferred
     */
    val PixelHeightPattern = Regex("""x(\d+)""")

    val ScaledFactorSpecifierPattern = Regex("""@([1-9]+[0-9]*)x""")

    val SVGExtension = "svg"

    val RasterizeFlag = "rasterize"
    val RasterizeFlagShort = "r"

    /** Do your work here.
     * @return whether the operation did something or not */
    override fun operate(file: ResourceFile): Boolean {
        if (file.extension == SVGExtension && (file.flags.contains(RasterizeFlag) || file.flags.contains(RasterizeFlagShort))) {

            fun rasterize(factor: Int) {
                val resultFile = newFileNamed(file, file.name + (if (factor == 1) "" else "@" + factor + "x"), "png")
                val `in` = FileInputStream(file.file)
                val input = TranscoderInput(`in`)
                val out = FileOutputStream(resultFile)
                val output = TranscoderOutput(out)

                transcoder.transcodingHints.clear()
                transcoder.addTranscodingHint(ImageTranscoder.KEY_BACKGROUND_COLOR, Color(0, 0, 0, 0))
                file.flags.matchFirst(PixelSizePattern) { (width, height) ->
                    transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width.toInt().toFloat() * factor)
                    transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, height.toInt().toFloat() * factor)
                } ?: file.flags.matchFirst(PixelWidthPattern) { (width) ->
                    transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width.toInt().toFloat() * factor)
                } ?: file.flags.matchFirst(PixelHeightPattern) { (height) ->
                    transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, height.toInt().toFloat() * factor)
                }

                transcoder.transcode(input, output)
                out.flush()
                out.close()
                `in`.close()

                file.parent.addChild(resultFile)
                file.removeFromParent()
                Log.info(Name, "Svg rasterized. " + file.name + " (factor " + factor + "x)")
            }

            rasterize(1)

            if (file.flags.contains("scaled")) {
                val scales = IntSet()
                var parent = file.parent
                while (true) {
                    parent.flags.matchAll(ScaledFactorSpecifierPattern) { (factorStr) ->
                        val factor = factorStr.toInt()

                        if (scales.add(factor)) {
                            rasterize(factor)
                        }
                    }

                    if (parent == parent.parent) {
                        break
                    } else {
                        parent = parent.parent
                    }
                }
            }

            return true
        }

        return false
    }
}
