package darkyenus.resourcepacker.tasks

import java.awt.Color
import java.io.{FileInputStream, FileOutputStream}

import com.esotericsoftware.minlog.Log
import darkyenus.resourcepacker.{ResourceFile, Task}
import org.apache.batik.transcoder.image.{ImageTranscoder, PNGTranscoder}
import org.apache.batik.transcoder.{SVGAbstractTranscoder, TranscoderInput, TranscoderOutput}

/**
 * Raterizes .svg files using default size (in file) or one specified in flags.
 *
 * Flags:
 * {{{
 *   .[W]x[H].
 *   W - width in pixels
 *   H - height in pixels
 *
 *   One of them can be left blank to infer the size by maintaining ratio.
 * }}}
 *
 * @author Darkyen
 */
object RasterizeTask extends Task {
  private val transcoder = new PNGTranscoder()

  /**
  Example:
	450x789   => Image will be 450 pixels wide and 789 pixels tall
    */
  val PixelSizePattern = """(\d+)x(\d+)""".r

  /**
  Example:
    450x   => Image will be 450 pixels wide and height will be inferred
    */
  val PixelWidthPattern = """(\d+)x""".r

  /**
  Example:
    x789   => Image will be 789 pixels tall and width will be inferred
    */
  val PixelHeightPattern = """x(\d+)""".r

  val SVGExtension = "svg"

  /** Do your work here.
    * @return whether the operation did something or not */
  override def operate(svg: ResourceFile): Boolean = {
    if (svg.extension.equals(SVGExtension)) {
      val resultFile = newFile(svg, "png")
      val in = new FileInputStream(svg.file)
      val input = new TranscoderInput(in)
      val out = new FileOutputStream(resultFile)
      val output = new TranscoderOutput(out)

      transcoder.getTranscodingHints.clear()
      transcoder.addTranscodingHint(ImageTranscoder.KEY_BACKGROUND_COLOR, new Color(0, 0, 0, 0))
      svg.flags.collectFirst {
        case PixelSizePattern(width, height) =>
          transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width.toInt.toFloat)
          transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, height.toInt.toFloat)
        case PixelWidthPattern(width) =>
          transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width.toInt.toFloat)
        case PixelHeightPattern(height) =>
          transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, height.toInt.toFloat)
      }

      transcoder.transcode(input, output)
      out.flush()
      out.close()
      in.close()

      svg.parent.addChild(resultFile)
      svg.removeFromParent()
      Log.info(Name, "Svg rasterized. " + svg.name)
      true
    } else false
  }
}
