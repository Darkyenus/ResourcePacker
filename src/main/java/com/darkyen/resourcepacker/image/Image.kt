package com.darkyen.resourcepacker.image

import com.badlogic.gdx.math.MathUtils
import com.darkyen.resourcepacker.Resource
import com.darkyen.resourcepacker.SettingKey
import com.darkyen.resourcepacker.util.batik.SVGFile
import com.esotericsoftware.minlog.Log
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.BufferedInputStream
import java.io.FileInputStream
import javax.imageio.ImageIO
import com.darkyen.resourcepacker.isBitmapImage
import com.darkyen.resourcepacker.isVectorImage
import com.darkyen.resourcepacker.util.*
import com.google.common.io.Files
import java.awt.image.WritableRaster
import java.io.File
import java.util.*

/**
 * Examples:
 * w15h1    => Image will be 15 tile-sizes wide and 1 tile size tall
 * w1,5h0,5 => Image will be 1.5 tile-sizes wide and 0.5 tile size tall
 *
 * Width or height may be '?', to signify that it is specified by PixelSizePattern
 * or that it should be derived by the aspect ratio of the file.
 */
val TileSizePattern = Regex("""w((?:\d+(?:,\d+)?)|\?)h((?:\d+(?:,\d+)?)|\?)""")

/**
 * Example:
 * 450x789   => Image will be 450 pixels wide and 789 pixels tall
 *
 * Width or height may be '?', to signify that it is specified by PixelSizePattern
 * or that it should be derived by the aspect ratio of the file.
 */
val PixelSizePattern = Regex("""((?:\d+)|\?)x((?:\d+)|\?)""")

/**
 * Matches: #RRGGBB
 * Where RR (GG and BB) are hexadecimal digits.
 * Example:
 * #FF0056
 * to capture groups RR GG BB
 */
val PreBlendRegex = Regex("#$ColorRegexGroup")

/**
 * Matches: scaling <algo>
 * Where <algo> is a name of one of the algorithms in [ImageScaling], by [ImageScaling.scalingName].
 * Example:
 * scaling bilinear
 */
val ScalingRegex = Regex("scaling (\\w+)")

val TileSize: SettingKey<Int> = SettingKey("TileSize", 128, "Size of tile used by w<W>h<H> flag pattern")

val DefaultImageScaling: SettingKey<ImageScaling> = SettingKey("DefaultImageScaling", ImageScaling.Bilinear, "Image scaling algorithm used by default")

private fun tileFraction(input: String): Int {
    if (input.isEmpty()) return -1
    return Math.round(input.replace(',', '.').toFloat() * TileSize.get())
}

/**
 *
 */
sealed class Image(val file:Resource.ResourceFile, private val canBeNinepatch:Boolean) {

    private var _width:Int = -1
    private var _height:Int = -1
    protected var _fileWidth:Int = -1
    protected var _fileHeight:Int = -1
    protected var _backgroundColor:Color? = null
    protected var _scaling:ImageScaling? = null
    protected var _ninepatch:Boolean = false
    protected var _ninepatchSplits:IntArray? = null
    protected var _ninepatchPads:IntArray? = null

    /**
     * Ensures that _width and _height contains correct values by inspecting the flags and the file.
     *
     * The logic is following:
     * 1. Apply dimensions from TileSizePattern
     * 2. Apply unspecified dimensions from PixelSizePattern
     * 3. If both dimensions unspecified, specify them by inspecting the file
     * 4. If one of dimensions unspecified, inspect the file's aspect ratio and calculate the missing dimension,
     *      using it and the aspect ratio
     */
    private fun ensureImagePrepared() {
        if (_width == -1 || _height == -1) {
            // Ninepatch
            if (file.flags.contains("9")) {
                if (canBeNinepatch) {
                    _ninepatch = true
                } else {
                    Log.warn("Image", "$file has ninepatch flag, but this type of images can't work as ninepatch")
                }
            }

            // Background color
            file.flags.matchFirst(PreBlendRegex) { (color) ->
                _backgroundColor = parseHexColor(color).toAwt()
            }

            // Scaling
            file.flags.matchFirst(ScalingRegex) { (algo) ->
                for (value in ImageScaling.values()) {
                    if (value.scalingName.equals(algo, ignoreCase = true)) {
                        _scaling = value
                        return@matchFirst
                    }
                }
                Log.warn("Image", "Unknown scaling algorithm '$algo'")
            }

            // Dimensions
            setupDimensions()
            if (_fileWidth < 0 || _fileHeight < 0) {
                throw IllegalStateException("Dimensions of $file could not be determined")
            }

            if (_ninepatch && _ninepatchSplits == null) {
                Log.warn("Image", "$file claims to be a ninepatch, but does not have splits - losing ninepatch status")
                _ninepatch = false
            }

            var newWidth:Int = -1
            var newHeight:Int = -1
            file.flags.matchFirst(TileSizePattern) {
                (tileWidth, tileHeight) ->
                newWidth = tileFraction(tileWidth)
                newHeight = tileFraction(tileHeight)

                Log.debug("Image", "Size of $file determined by tile pattern to be ${newWidth}x$newHeight")
            }
            file.flags.matchFirst(PixelSizePattern) {
                (width, height) ->
                if (width != "?") {
                    if (newWidth != -1) {
                        Log.warn("Image", "File $file has width set by both tile and by pixels! Using size by tile.")
                    } else {
                        newWidth = width.toInt()
                    }
                }

                if (height != "?") {
                    if (newHeight != -1) {
                        Log.warn("Image", "File $file has height set by both tile and by pixels! Using size by tile.")
                    } else {
                        newHeight = height.toInt()
                    }
                }

                Log.debug("Image", "Size of $file determined by pixel pattern to be ${newWidth}x$newHeight")
            }

            // Already specified fully by flags
            if (newWidth != -1 && newHeight != -1) {
                _width = newWidth
                _height = newHeight

                val ratio = newWidth.toFloat() / newHeight
                val fileRatio = _fileWidth.toFloat() / _fileHeight
                if (!MathUtils.isEqual(ratio, fileRatio)) {
                    Log.debug("Image", "Specified dimensions of $file have different ratio than the file dimensions, image may be distorted")
                }
                return
            }

            // Not specified at all by flags
            if (newWidth == -1 && newHeight == -1) {
                _width = _fileWidth
                _height = _fileHeight
                return
            }

            // Partly specified by flags
            if (_fileWidth == 0 || _fileHeight == 0) {
                Log.warn("Dimensions of $file are degenerate (w: $_width h: $_height)")
                if (newWidth != -1) {
                    _width = newWidth
                    _height = _fileHeight
                } else {
                    _width = _fileWidth
                    _height = newHeight
                }
                return
            }

            if (newWidth == -1) {
                newWidth = Math.round((_width.toDouble() / _height.toDouble()) * newHeight).toInt()
            } else {
                newHeight = Math.round((_height.toDouble() / _width.toDouble()) * newWidth).toInt()
            }
            _width = newWidth
            _height = newHeight
            Log.debug("Dimensions of $file derived to be ${newWidth}x$newHeight")
        }
    }

    protected abstract fun setupDimensions()

    /** without ninepatch data if ninepatch */
    val width:Int
        get() {
            ensureImagePrepared()
            return _width
        }

    /** without ninepatch data if ninepatch */
    val height:Int
        get() {
            ensureImagePrepared()
            return _height
        }

    /** without ninepatch data if ninepatch */
    val originalWidth:Int
        get() {
            ensureImagePrepared()
            return _fileWidth
        }

    /** without ninepatch data if ninepatch */
    val originalHeight:Int
        get() {
            ensureImagePrepared()
            return _fileHeight
        }

    val backgroundColor:Color?
        get() {
            ensureImagePrepared()
            return _backgroundColor
        }

    val scaling:ImageScaling
        get() {
            ensureImagePrepared()
            return _scaling ?: DefaultImageScaling.get()
        }

    val ninepatch:Boolean
        get() {
            ensureImagePrepared()
            return _ninepatch
        }

    private fun scale(width: Int, height: Int, ninepatchData:IntArray?):IntArray? {
        if (ninepatchData == null) {
            return null
        }
        val scaleX = width.toDouble() / originalWidth.toDouble()
        val scaleY = height.toDouble() / originalHeight.toDouble()
        return intArrayOf(
                Math.round(ninepatchData[0] * scaleX).toInt(),
                Math.round(ninepatchData[1] * scaleX).toInt(),
                Math.round(ninepatchData[2] * scaleY).toInt(),
                Math.round(ninepatchData[3] * scaleY).toInt()
        )
    }

    fun ninepatchSplits(width: Int, height: Int):IntArray? {
        ensureImagePrepared()
        return scale(width, height, _ninepatchSplits)
    }

    fun ninepatchPads(width: Int, height: Int):IntArray? {
        ensureImagePrepared()
        return scale(width, height, _ninepatchPads)
    }

    abstract fun image(width: Int = this.width, height: Int = this.height, background: Color? = backgroundColor):BufferedImage

    abstract fun dispose()

    internal class BitmapImage(file:Resource.ResourceFile) : Image(file, true) {

        override fun dispose() {
            _image = null
        }

        private var _image:BufferedImage? = null

        private fun image():BufferedImage {
            var _image = _image
            if (_image == null) {
                _image = ImageIO.read(file.file) ?: throw IllegalStateException("File does not exist! This shouldn't happen. (" + file.file.canonicalPath + ") " + file)
                this._image = _image
            }
            return _image

        }

        override fun setupDimensions() {
            val image = image()
            _fileWidth = image.width
            _fileHeight = image.height

            if (_ninepatch) {
                // Mine ninepatch data from the image
                _ninepatchSplits = getSplits(image)
                _ninepatchPads = getPads(image, _ninepatchSplits)

                val strippedImage = BufferedImage(image.width - 2, image.height - 2, BufferedImage.TYPE_INT_ARGB)
                val g = strippedImage.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
                g.drawImage(image, 0, 0, image.width, image.height, 1, 1, image.width + 1, image.height + 1, null)
                g.dispose()

                _image = strippedImage
                _fileWidth -= 2
                _fileHeight -= 2
            }
        }

        override fun image(width: Int, height: Int, background: Color?): BufferedImage {
            return resizeImage(image(), width, height, background, scaling)
        }

    }

    internal class VectorImage(file:Resource.ResourceFile) : Image(file, false) {

        private var documentCache:SVGFile? = null

        private val document:SVGFile
            get() {
                var documentCache = this.documentCache
                if (documentCache == null) {
                    documentCache = SVGFile(file.file.toURI().toString(), BufferedInputStream(FileInputStream(file.file)))
                    this.documentCache = documentCache
                }
                return documentCache
            }

        override fun setupDimensions() {
            _fileWidth = Math.round(document.docWidth).toInt()
            _fileHeight = Math.round(document.docHeight).toInt()
        }

        override fun image(width: Int, height: Int, background: Color?): BufferedImage {
            return document.rasterize(width, height, background)
        }

        override fun dispose() {
            val documentCache = this.documentCache
            if (documentCache != null) {
                documentCache.dispose()
                this.documentCache = null
            }
        }
    }

    /* Some code based on code from libGDX, under http://www.apache.org/licenses/LICENSE-2.0 */

    /**
     * Returns the splits, or null if the image had no splits or the splits were only a single region. Splits are an int[4] that
     * has left, right, top, bottom.
     */
    protected fun getSplits(image: BufferedImage): IntArray? {
        val raster = image.raster

        var startX = getSplitPoint(raster, 1, 0, true, true)
        var endX = getSplitPoint(raster, startX, 0, false, true)
        var startY = getSplitPoint(raster, 0, 1, true, false)
        var endY = getSplitPoint(raster, 0, startY, false, false)

        // Ensure pixels after the end are not invalid.
        getSplitPoint(raster, endX + 1, 0, true, true)
        getSplitPoint(raster, 0, endY + 1, true, false)

        // No splits, or all splits.
        if (startX == 0 && endX == 0 && startY == 0 && endY == 0) return null

        // Subtraction here is because the coordinates were computed before the 1px border was stripped.
        if (startX != 0) {
            startX--
            endX = raster.width - 2 - (endX - 1)
        } else {
            // If no start point was ever found, we assume full stretch.
            endX = raster.width - 2
        }
        if (startY != 0) {
            startY--
            endY = raster.height - 2 - (endY - 1)
        } else {
            // If no start point was ever found, we assume full stretch.
            endY = raster.height - 2
        }

        return intArrayOf(startX, endX, startY, endY)
    }

    /**
     * Returns the pads, or null if the image had no pads or the pads match the splits.
     * Pads are an int[4] that has left, right, top, bottom.
     */
    protected fun getPads(image: BufferedImage, splits: IntArray?): IntArray? {
        val raster = image.raster

        val bottom = raster.height - 1
        val right = raster.width - 1

        var startX = getSplitPoint(raster, 1, bottom, true, true)
        var startY = getSplitPoint(raster, right, 1, true, false)

        // No need to hunt for the end if a start was never found.
        var endX = 0
        var endY = 0
        if (startX != 0) endX = getSplitPoint(raster, startX + 1, bottom, false, true)
        if (startY != 0) endY = getSplitPoint(raster, right, startY + 1, false, false)

        // Ensure pixels after the end are not invalid.
        getSplitPoint(raster, endX + 1, bottom, true, true)
        getSplitPoint(raster, right, endY + 1, true, false)

        // No pads.
        if (startX == 0 && endX == 0 && startY == 0 && endY == 0) {
            return null
        }

        // -2 here is because the coordinates were computed before the 1px border was stripped.
        if (startX == 0 && endX == 0) {
            startX = -1
            endX = -1
        } else {
            if (startX > 0) {
                startX--
                endX = raster.width - 2 - (endX - 1)
            } else {
                // If no start point was ever found, we assume full stretch.
                endX = raster.width - 2
            }
        }
        if (startY == 0 && endY == 0) {
            startY = -1
            endY = -1
        } else {
            if (startY > 0) {
                startY--
                endY = raster.height - 2 - (endY - 1)
            } else {
                // If no start point was ever found, we assume full stretch.
                endY = raster.height - 2
            }
        }

        val pads = intArrayOf(startX, endX, startY, endY)

        return if (splits != null && Arrays.equals(pads, splits)) {
            null
        } else pads
    }

    /**
     * Hunts for the start or end of a sequence of split pixels. Begins searching at (startX, startY) then follows along the x or y
     * axis (depending on value of xAxis) for the first non-transparent pixel if startPoint is true, or the first transparent pixel
     * if startPoint is false. Returns 0 if none found, as 0 is considered an invalid split point being in the outer border which
     * will be stripped.
     */
    private fun getSplitPoint(raster: WritableRaster, startX: Int, startY: Int, startPoint: Boolean, xAxis: Boolean): Int {
        val rgba = IntArray(4)

        var next = if (xAxis) startX else startY
        val end = if (xAxis) raster.width else raster.height
        val breakA = if (startPoint) 255 else 0

        var x = startX
        var y = startY
        while (next != end) {
            if (xAxis)
                x = next
            else
                y = next

            raster.getPixel(x, y, rgba)
            if (rgba[3] == breakA) return next

            if (!startPoint && (rgba[0] != 0 || rgba[1] != 0 || rgba[2] != 0 || rgba[3] != 255))
                splitError(x, y, rgba)

            next++
        }

        return 0
    }

    private fun splitError(x: Int, y: Int, rgba: IntArray) {
        throw RuntimeException("$file: Invalid ninepatch split pixel at $x, $y, rgba: ${rgba[0]}, ${rgba[1]}, ${rgba[2]}, ${rgba[3]}")
    }

    override fun toString(): String {
        return javaClass.simpleName+" from "+file
    }

    companion object {

        private fun resizeStep(current:Int, target:Int, scaling: ImageScaling):Int {
            if (!scaling.multiStepDownscale) {
                return target
            }

            if (target >= current) {
                // When making the image bigger, single step is enough
                return target
            }
            // Target is less than current
            if (current / 2 > target) {
                // Only halve it
                return current / 2
            }
            // Close enough, go to target
            return target
        }

        fun resizeImage(image:BufferedImage, width: Int, height: Int, background: Color?, scaling: ImageScaling = DefaultImageScaling.get()):BufferedImage {

            if (width == image.width && height == image.height && background == null) {
                return image
            }

            // Implements multi-step high quality resizing
            var currentStep = image
            var currentWidth = currentStep.width
            var currentHeight = currentStep.height

            // Do scaling
            do {
                val nextWidth = resizeStep(currentWidth, width, scaling)
                val nextHeight = resizeStep(currentHeight, height, scaling)

                val lastStep = nextWidth == width && nextHeight == height

                val resizedImage = BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_ARGB)
                val g = resizedImage.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scaling.awtFlag)

                if (lastStep && background != null) {
                    val oldColor = g.color
                    g.color = background
                    g.fillRect(0, 0, nextWidth, nextHeight)
                    g.color = oldColor
                }

                g.drawImage(currentStep, 0, 0, nextWidth, nextHeight, null)
                g.dispose()

                currentStep = resizedImage
                currentWidth = nextWidth
                currentHeight = nextHeight

            } while (!lastStep)

            return currentStep
        }
    }
}

/** Creates appropriate image from given file */
fun Resource.ResourceFile.createImage():Image? {
    if (isVectorImage()) {
        return Image.VectorImage(this)
    }

    if (isBitmapImage()) {
        return Image.BitmapImage(this)
    }

    return null
}

fun BufferedImage.saveToFile(file: File, format:String? = null) {
    var realFormat:String? = format
    if (realFormat == null) {
        realFormat = Files.getFileExtension(file.name)
        if (realFormat == null || realFormat.isBlank()) {
            realFormat = "png"
        }
    }
    ImageIO.write(this, realFormat, file)
}
