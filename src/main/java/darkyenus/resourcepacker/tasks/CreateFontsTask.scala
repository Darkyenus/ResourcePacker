package darkyenus.resourcepacker.tasks

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.IntSet
import com.darkyen.resourcepacker.util.FreeTypePacker
import com.esotericsoftware.minlog.Log
import com.google.common.io.Files
import com.darkyen.resourcepacker.util.FreeTypePacker.FreeTypeFontParameter
import darkyenus.resourcepacker.util.ImageUtil
import darkyenus.resourcepacker.{ResourceFile, Task}

/**
  * Rasterizes .ttf fonts.
  * Font must have .N. flag where N is font size in pixels.
  *
  * Flags:
  * {{{
  * <N> - size - mandatory
  *
  * <S>-<E> - adds all codepoints from S to E, inclusive. Missing glyphs are not added.
  *
  * bg#RRGGBBAA - background color, default is transparent
  *
  * fg#RRGGBBAA - foreground color, default is white
  *
  * outline <W> RRGGBBAA [straight] - add outline of width W and specified color, add "straight" for sharp/mitered edges
  * }}}
  *
  * When no glyphs are specified, all available glyphs are added.
  *
  * @author Darkyen
  */
object CreateFontsTask extends Task {

  private val HexColorCapture = "([0-9A-Fa-f]{1,8})"

  private def parseHexColor(hex:String):Color = {
    hex.length match {
      case 1 => //Gray
        val gray = (Integer.parseInt(hex, 16) * 0xF) min 255
        new Color(gray / 255f, gray / 255f, gray / 255f, 1f)
      case 2 => //Gray with alpha
        val gray = (Integer.parseInt(hex.substring(0,1), 16) * 0xF) min 255
        val alpha = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF) min 255
        new Color(gray, gray, gray, alpha)
      case 3 => //RGB
        val r = (Integer.parseInt(hex.substring(0,1), 16) * 0xF) min 255
        val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF) min 255
        val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF) min 255
        new Color(r / 255f,g / 255f,b / 255f, 1f)
      case 4 => //RGBA
        val r = (Integer.parseInt(hex.substring(0,1), 16) * 0xF) min 255
        val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF) min 255
        val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF) min 255
        val a = (Integer.parseInt(hex.substring(3, 4), 16) * 0xF) min 255
        new Color(r / 255f,g / 255f,b / 255f,a / 255f)
      case 5 => //RGBAA
        val r = (Integer.parseInt(hex.substring(0,1), 16) * 0xF) min 255
        val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF) min 255
        val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF) min 255
        val a = Integer.parseInt(hex.substring(3, 5), 16)
        new Color(r / 255f,g / 255f,b / 255f,a / 255f)
      case 6 => //RRGGBB
        val r = Integer.parseInt(hex.substring(0, 2), 16)
        val g = Integer.parseInt(hex.substring(2, 4), 16)
        val b = Integer.parseInt(hex.substring(4, 6), 16)
        new Color(r / 255f,g / 255f,b / 255f,1f)
      case 7 => //RRGGBBA
        val r = Integer.parseInt(hex.substring(0, 2), 16)
        val g = Integer.parseInt(hex.substring(2, 4), 16)
        val b = Integer.parseInt(hex.substring(4, 6), 16)
        val a = (Integer.parseInt(hex.substring(6, 7), 16) * 0xF) min 255
        new Color(r / 255f,g / 255f,b / 255f,a / 255f)
      case 8 => //RRGGBBAA
        val r = Integer.parseInt(hex.substring(0, 2), 16)
        val g = Integer.parseInt(hex.substring(2, 4), 16)
        val b = Integer.parseInt(hex.substring(4, 6), 16)
        val a = Integer.parseInt(hex.substring(6, 8), 16)
        new Color(r / 255f,g / 255f,b / 255f,a / 255f)
    }
  }

  /* Examples:
   * pt56     => Padding left 56
   * p89      => Padding everywhere 89
   */
  val PaddingRegex = "p(t|b|l|r|)(\\d+)".r

  /* Example:
   * 14          => Size 14
   */
  val SizeRegex = "(\\d+)".r

  /* Example:
   * 56-67     => Glyphs 56 to 67 should be added
   */
  val GlyphRangeRegex = "(\\d+)-(\\d+)".r

  /* Example:
   * outline 4 FF0000 miter   => Will create solid red 4px outline with miter joins
   */
  val OutlineRegex = s"outline (\\d+) $HexColorCapture ?(\\w+)?".r
  //outline (\\d+) ([0-9A-Fa-f]{3,8}) ?(\\w+)?

  /** Matches bg#RRGGBBAA colors for background. Default is Transparent. */
  val BGRegex = s"bg#$HexColorCapture".r
  /** Matches bg#RRGGBBAA colors for foreground (color of font). Default is White. */
  val FGRegex = s"fg#$HexColorCapture".r



  override def operate(fontFile: ResourceFile): Boolean = {
    if (fontFile.isFont) {
      val params = fontFile.flags

      var size: Int = -1
      var paddingLeft = 0
      var paddingRight = 0
      var paddingTop = 0
      var paddingBottom = 0

      fontFile.flags foreach {
        case PaddingRegex(side, amountString) =>
          val amount = amountString.toInt
          side match {
            case "l" => paddingLeft = amount
            case "r" => paddingRight = amount
            case "t" => paddingTop = amount
            case "b" => paddingBottom = amount
            case "" =>
              paddingLeft = amount
              paddingRight = amount
              paddingTop = amount
              paddingBottom = amount
          }
        case SizeRegex(sizeString) =>
          size = sizeString.toInt
        case _ =>
      }

      if (size < 0) {
        Log.debug(Name, "Not rasterizing font, size not specified." + fontFile)
      } else if (size == 0) {
        Log.error(Name, "Size must be bigger than 0. " + fontFile)
      } else {
        val parameter = new FreeTypeFontParameter
        parameter.fontName = fontFile.name
        parameter.size = size


        params collectFirst {
          case OutlineRegex(width, hexColor, optJoin) =>
            parameter.borderWidth = Integer.parseInt(width)
            parameter.borderColor = parseHexColor(hexColor)
            parameter.borderStraight = "straight".equalsIgnoreCase(optJoin)
        }

        params collectFirst {
          case FGRegex(hexColor) =>
            parseHexColor(hexColor)
        } match {
          case Some(color) =>
            parameter.color = color
          case None =>
        }

        val glyphsToAdd = new IntSet()
        for (p <- params) {
          p match {
            case GlyphRangeRegex(start, end) =>
              val from = start.toInt
              val to = end.toInt
              for (i <- from to to) {
                glyphsToAdd.add(i)
              }
              Log.debug(Name, s"Added glyphs from $from to $to.")
            case _ =>
          }
        }
        if (glyphsToAdd.size != 0) {
          parameter.codePoints = glyphsToAdd
        }

        val outputFolder = newFolder()
        val packedFiles = FreeTypePacker.pack(fontFile.file, outputFolder, parameter)

        val pageCount = packedFiles.size - 1
        if (pageCount <= 0) {
          Log.warn(Name, "Font didn't render on any pages. " + fontFile)
        } else if (pageCount > 1) {
          Log.warn(Name, "Font did render on more than one page. This may case problems when loading for UI skin. " + pageCount + " " + fontFile)
        }
        Log.info(Name, "Font created. " + fontFile)
        fontFile.parent.removeChild(fontFile)

        val bgColor = params collectFirst {
          case BGRegex(hexColor) =>
            Log.debug(Name, "Background color for font set. " + fontFile)
            parseHexColor(hexColor)
        }

        for (generatedJavaFile <- packedFiles) {
          if (Files.getFileExtension(generatedJavaFile.getName).equalsIgnoreCase("png")) {
            ImageUtil.clampImage(generatedJavaFile)
            for (backgroundColor <- bgColor) {
              val jColor = new java.awt.Color(backgroundColor.r, backgroundColor.g, backgroundColor.b, backgroundColor.a)
              ImageUtil.preBlendImage(generatedJavaFile, jColor)
            }
          }
          val f = fontFile.parent.addChild(generatedJavaFile)
          Log.debug(Name, "Font file added. " + f)
        }
      }
      true
    } else false
  }
}
