package darkyenus.resourcepacker.tasks

import java.awt.Color
import java.io.{FileInputStream, FileOutputStream}

import com.badlogic.gdx.utils.IntSet
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
 *
 *   .scaled.
 *   Will check parent directories. If one of them contains flags in form @Nx, where N is a whole number,
 *   rasterizer will additionally generate png with dimensions*N for each such flag and save it into png with @Nx
 *   appended, unless it already exists. (In that case, no extra image is generated)
 * }}}
 *
 *
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

  val ScaledFactorSpecifierPattern = """\@([1-9]+[0-9]*)x""".r

  val SVGExtension = "svg"

  /** Do your work here.
    * @return whether the operation did something or not */
  override def operate(svg: ResourceFile): Boolean = {
    if (svg.extension.equals(SVGExtension)) {

      def rasterize(factor:Int): Unit ={
        val resultFile = newFileNamed(svg, svg.name + (if(factor == 1) "" else "@"+factor+"x"), "png")
        val in = new FileInputStream(svg.file)
        val input = new TranscoderInput(in)
        val out = new FileOutputStream(resultFile)
        val output = new TranscoderOutput(out)

        transcoder.getTranscodingHints.clear()
        transcoder.addTranscodingHint(ImageTranscoder.KEY_BACKGROUND_COLOR, new Color(0, 0, 0, 0))
        svg.flags.collectFirst {
          case PixelSizePattern(width, height) =>
            transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width.toInt.toFloat * factor)
            transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, height.toInt.toFloat * factor)
          case PixelWidthPattern(width) =>
            transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, width.toInt.toFloat * factor)
          case PixelHeightPattern(height) =>
            transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, height.toInt.toFloat * factor)
        }

        transcoder.transcode(input, output)
        out.flush()
        out.close()
        in.close()

        svg.parent.addChild(resultFile)
        svg.removeFromParent()
        Log.info(Name, "Svg rasterized. " + svg.name+" (factor "+factor+"x)")
      }

      rasterize(1)

      if(svg.flags.contains("scaled")){
        val scales = new IntSet()
        var parent = svg.parent
        while(parent != null){
          parent.flags.flatMap{
            case ScaledFactorSpecifierPattern(factorStr) =>
              List(factorStr.toInt)
            case _ => Nil
          }.foreach(scales.add)

          if(parent == parent.parent){
            parent = null
          }else{
            parent = parent.parent
          }
        }

        val iterator = scales.iterator()
        while(iterator.hasNext){
          rasterize(iterator.next())
        }
      }

      true
    } else false
  }
}
