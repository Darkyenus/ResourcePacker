package darkyenus.resourcepacker.tasks

import java.awt._
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

import com.badlogic.gdx.math.MathUtils
import com.esotericsoftware.minlog.Log
import darkyenus.resourcepacker.{ResourceFile, SettingKey, Task}

/**
 * Resizes image by given arguments
 *
 * @author Darkyen
 */
object ResizeTask extends Task {
  /**
  Examples:
    w15h1    => Image will be 15 tile-sizes wide and 1 tile size tall
    w1,5h0,5 => Image will be 1.5 tile-sizes wide and 0.5 tile size tall
    */
  val TileSizePattern = """w(\d+(?:,\d+)?)h(\d+(?:,\d+)?)""".r

  /**
  Example:
  450x789   => Image will be 450 pixels wide and 789 pixels tall
    */
  val PixelSizePattern = """(\d+)x(\d+)""".r

  val TileSize = new SettingKey[Int]("TileSize", 128, "Size of tile used by ResizeTask's w<W>h<H> flag pattern")

  def tileFraction(input: String): Int = {
    (input.replace(',', '.').toFloat * TileSize.get()).round
  }

  override def operate(file: ResourceFile): Boolean = {
    if (file.isImage) {
      file.flags.collectFirst {
        case TileSizePattern(tileWidth, tileHeight) if tileWidth != null && tileHeight != null => (tileFraction(tileWidth), tileFraction(tileHeight))
        case PixelSizePattern(pixelWidth, pixelHeight) if pixelWidth != null && pixelHeight != null => (pixelWidth.toInt, pixelHeight.toInt)
      } match {
        case Some((width, height)) =>
          val desiredRatio = width.toFloat / height

          val rawImage = ImageIO.read(file.file)
          if (rawImage == null) {
            Log.error(Name, "File does not exist! This shouldn't happen. (" + file.file.getCanonicalPath + ") " + file)
          }
          val currentRatio = rawImage.getWidth.toFloat / rawImage.getHeight
          if (!MathUtils.isEqual(desiredRatio, currentRatio)) {
            Log.warn(Name, s"Desired ratio and current ratio of ${file.file.getAbsolutePath} differ (Desired: $desiredRatio Current: $currentRatio)")
          }
          if (width > rawImage.getWidth || height > rawImage.getHeight) {
            Log.warn(Name, s"Resizing of ${file.file.getAbsolutePath} would lead to upsampling, skipping.")
          } else {
            val resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
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
        case None =>
      }
    }
    false
  }
}








