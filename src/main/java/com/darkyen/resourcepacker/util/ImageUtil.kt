@file:Suppress("unused")

package com.darkyen.resourcepacker.util

import com.google.common.io.Files
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.LookupOp
import java.awt.image.LookupTable
import java.io.File
import javax.imageio.ImageIO

/**
 * Collection of methods operating on images in place, implemented with java.awt.
 *
 * @author Darkyen
 */


/** Renders image onto given color, can be used to effectively remove alpha. */
fun preBlendImage(imageFile: File, color: Color, ninepatch: Boolean = false) {
    val originalImage = ImageIO.read(imageFile) ?: error("Couldn't load image to preBlend " + imageFile.canonicalPath)

    val resultImage = BufferedImage(originalImage.width, originalImage.height, originalImage.type)
    val g = resultImage.createGraphics()
    g.background = Color(0, 0, 0, 0)
    g.clearRect(0, 0, originalImage.width, originalImage.height)
    g.color = color
    if (ninepatch) {
        g.fillRect(1, 1, originalImage.width - 2, originalImage.height - 2)
    } else {
        g.fillRect(0, 0, originalImage.width, originalImage.height)
    }
    g.drawImage(originalImage, 0, 0, null)

    g.dispose()

    ImageIO.write(resultImage, Files.getFileExtension(imageFile.name), imageFile)
    if (resultImage.width != originalImage.width || resultImage.height != originalImage.height) {
        error("Something very weird happened while pre-blending ow: " + originalImage.width + " oh: " + originalImage.height + " rw: " + resultImage.width + " rh: " + resultImage.height + " f: " + imageFile.canonicalPath)
    }
}

fun multiplyImage(imageFile: File, color: Color) {
    val originalImage = ImageIO.read(imageFile) ?: error("Couldn't load image to multiply " + imageFile.canonicalPath)
    val colorComponents = color.getRGBComponents(null)

    val filter = LookupOp(object : LookupTable(0, 4) {
        override fun lookupPixel(src: IntArray, dst: IntArray?): IntArray {
            val result = dst ?: IntArray(src.size)
            for (i in 0..3) {
                result[i] = ((src[i] / 255f * colorComponents[i]) * 255f).toInt()
            }
            return result
        }
    }, null)

    val resultImage = filter.filter(originalImage, BufferedImage(originalImage.width, originalImage.height, originalImage.type))

    ImageIO.write(resultImage, Files.getFileExtension(imageFile.name), imageFile)
    if (resultImage.width != originalImage.width || resultImage.height != originalImage.height) {
        error("Something very weird happened while multiplying ow: " + originalImage.width + " oh: " + originalImage.height + " rw: " + resultImage.width + " rh: " + resultImage.height + " f: " + imageFile.canonicalPath)
    }
}

fun clampImage(imageFile: File) {
    val originalImage = ImageIO.read(imageFile) ?: error("Couldn't load image to clamp " + imageFile.canonicalPath)
    val raster = originalImage.data
    val w = raster.width
    val h = raster.height

    fun isCullable(xs: Int, ys: Int, dx: Int, dy: Int): Boolean {
        if (xs < 0 || ys < 0 || xs >= w || ys >= h) return false

        var x = xs
        var y = ys

        val Alpha = 3
        val pix = IntArray(4)
        var cullable = true
        while (cullable && x >= 0 && y >= 0 && x < w && y < h) {
            raster.getPixel(x, y, pix)

            if (pix[Alpha] > 0) {
                cullable = false
            }

            x += dx
            y += dy
        }
        return cullable
    }

    var cullableFromBottom = 0
    while (isCullable(0, h - 1 - cullableFromBottom, 1, 0)) {
        cullableFromBottom += 1
    }
    var cullableFromRight = 0
    while (isCullable(w - 1 - cullableFromRight, h - 1 - cullableFromBottom, 0, -1)) {
        cullableFromRight += 1
    }

    val culledWidth = Math.max(w - cullableFromRight, 1)
    val culledHeight = Math.max(h - cullableFromBottom, 1)

    val resultImage = BufferedImage(culledWidth, culledHeight, originalImage.type)
    val resultG = resultImage.createGraphics()
    resultG.background = Color(0, 0, 0, 0)
    resultG.clearRect(0, 0, resultImage.width, resultImage.height)
    resultG.drawImage(originalImage, 0, 0, null)
    resultG.dispose()

    ImageIO.write(resultImage, Files.getFileExtension(imageFile.name), imageFile)
    println("Culled " + cullableFromRight + " (to " + resultImage.width + ") from right and " + cullableFromBottom + " (to " + resultImage.height + ") from bottom on " + imageFile.name)
}