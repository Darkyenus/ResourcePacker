package darkyenus.resourcepacker.util

import java.awt.Color
import java.awt.image.{BufferedImage, LookupOp, LookupTable}
import java.io.File
import javax.imageio.ImageIO

import com.google.common.io.Files

/**
 * Collection of methods operating on images in place, implemented with java.awt.
 *
 * @author Darkyen
 */
object ImageUtil {

  /** Renders image onto given color, can be used to effectively remove alpha. */
  def preBlendImage(imageFile: File, color: Color, ninepatch: Boolean = false): Unit = {
    val originalImage = ImageIO.read(imageFile)
    if (originalImage == null) {
      sys.error("Couldn't load image to preBlend " + imageFile.getCanonicalPath)
    }

    val resultImage = new BufferedImage(originalImage.getWidth, originalImage.getHeight, originalImage.getType)
    val g = resultImage.createGraphics()
    g.setBackground(new Color(0, 0, 0, 0))
    g.clearRect(0, 0, originalImage.getWidth, originalImage.getHeight)
    g.setColor(color)
    if (ninepatch) {
      g.fillRect(1, 1, originalImage.getWidth - 2, originalImage.getHeight - 2)
    } else {
      g.fillRect(0, 0, originalImage.getWidth, originalImage.getHeight)
    }
    g.drawImage(originalImage, 0, 0, null)

    g.dispose()

    ImageIO.write(resultImage, Files.getFileExtension(imageFile.getName), imageFile)
    if (resultImage.getWidth != originalImage.getWidth || resultImage.getHeight != originalImage.getHeight) {
      sys.error("Something very weird happened while preblending ow: " + originalImage.getWidth + " oh: " + originalImage.getHeight + " rw: " + resultImage.getWidth + " rh: " + resultImage.getHeight + " f: " + imageFile.getCanonicalPath)
    }
  }

  def multiplyImage(imageFile: File, color: Color): Unit = {
    val originalImage = ImageIO.read(imageFile)
    if (originalImage == null) {
      sys.error("Couldn't load image to multiply " + imageFile.getCanonicalPath)
    }
    val colorComponents = color.getRGBComponents(null)

    val filter = new LookupOp(new LookupTable(0, 4) {
      override def lookupPixel(src: Array[Int], dst: Array[Int]): Array[Int] = {
        val result = if (dst == null) new Array[Int](src.length) else dst
        for (i <- 0 until 4) {
          result(i) = ((src(i) / 255f * colorComponents(i)) * 255f).toInt
        }
        result
      }
    }, null)

    val resultImage = filter.filter(originalImage, new BufferedImage(originalImage.getWidth, originalImage.getHeight, originalImage.getType))

    ImageIO.write(resultImage, Files.getFileExtension(imageFile.getName), imageFile)
    if (resultImage.getWidth != originalImage.getWidth || resultImage.getHeight != originalImage.getHeight) {
      sys.error("Something very weird happened while multiplying ow: " + originalImage.getWidth + " oh: " + originalImage.getHeight + " rw: " + resultImage.getWidth + " rh: " + resultImage.getHeight + " f: " + imageFile.getCanonicalPath)
    }
  }

  def clampImage(imageFile: File): Unit = {
    val originalImage = ImageIO.read(imageFile)
    if (originalImage == null) {
      sys.error("Couldn't load image to clamp " + imageFile.getCanonicalPath)
    }
    val raster = originalImage.getData
    val w = raster.getWidth
    val h = raster.getHeight

    def isCullable(xs: Int, ys: Int, dx: Int, dy: Int): Boolean = {
      if(xs < 0 || ys < 0 || xs >= w || ys >= h) return false

      var x = xs
      var y = ys

      val Alpha = 3
      val pix = new Array[Int](4)
      var cullable = true
      while (cullable && x >= 0 && y >= 0 && x < w && y < h) {
        raster.getPixel(x, y, pix)

        if (pix(Alpha) > 0) {
          cullable = false
        }

        x += dx
        y += dy
      }
      cullable
    }

    var cullableFromBottom = 0
    while (isCullable(0, h - 1 - cullableFromBottom, 1, 0)) {
      cullableFromBottom += 1
    }
    var cullableFromRight = 0
    while (isCullable(w - 1 - cullableFromRight, h - 1 - cullableFromBottom, 0, -1)) {
      cullableFromRight += 1
    }

    val resultImage = new BufferedImage(w - cullableFromRight, h - cullableFromBottom, originalImage.getType)
    val resultG = resultImage.createGraphics()
    resultG.setBackground(new Color(0, 0, 0, 0))
    resultG.clearRect(0, 0, resultImage.getWidth, resultImage.getHeight)
    resultG.drawImage(originalImage, 0, 0, null)
    resultG.dispose()

    ImageIO.write(resultImage, Files.getFileExtension(imageFile.getName), imageFile)
    println("Culled " + cullableFromRight + " (to " + resultImage.getWidth + ") from right and " + cullableFromBottom + " (to " + resultImage.getHeight + ") from bottom on " + imageFile.getName)
  }
}
