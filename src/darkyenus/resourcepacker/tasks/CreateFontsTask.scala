package darkyenus.resourcepacker.tasks

import java.awt.{BasicStroke, Color, Font}
import java.io.File

import com.badlogic.gdx.tools.hiero.BMFontUtil
import com.badlogic.gdx.tools.hiero.unicodefont.effects.{OutlineEffect, ColorEffect, Effect}
import com.badlogic.gdx.tools.hiero.unicodefont.{HieroSettings, UnicodeFont}
import com.esotericsoftware.minlog.Log
import com.google.common.base.Charsets
import com.google.common.io.Files
import darkyenus.resourcepacker.util.ImageUtil
import darkyenus.resourcepacker.{ResourceFile, Task}

/**
 * Rasterizes .ttf fonts.
 * Font must have .N. flag where N is font size in pixels.
 *
 * Flags:
 * {{{
 *   p[t|b|l|r]<N> - Adds padding around every glyph (Top, Bottom, Left, Right or if no letter then everywhere)
 *
 * <N> - size - mandatory
 *
 * <S>-<E> - adds all codepoints from S to E, inclusive
 *
 * from <F> - adds all codepoints from file F, file must be only letters and in same directory, no extension either. UTF-8 encoding is assumed
 *
 * bg#RRGGBBAA - background color, default is transparent
 *
 * fg#RRGGBBAA - foreground color, default is white
 *
 * native - use native rendering
 *
 * ascii - add all ascii glyphs (codepoints 32 through 255)
 *
 * nehe - add all nehe glyphs (codepoints 32 through 128)
 * }}}
 *
 * When no glyphs are specified, all ASCII glyphs are added.
 *
 * @author Darkyen
 */
object CreateFontsTask extends Task {

  private val HexColorCapture = "([0-9A-Fa-f]{1,8})"

  private def parseHexColor(hex:String):Color = {
    hex.length match {
      case 1 => //Gray
        val gray = (Integer.parseInt(hex, 16) * 0xF) min 255
        new Color(gray, gray, gray)
      case 2 => //Gray with alpha
        val gray = (Integer.parseInt(hex.substring(0,1), 16) * 0xF) min 255
        val alpha = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF) min 255
        new Color(gray, gray, gray, alpha)
      case 3 => //RGB
        val r = (Integer.parseInt(hex.substring(0,1), 16) * 0xF) min 255
        val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF) min 255
        val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF) min 255
        new Color(r,g,b)
      case 4 => //RGBA
        val r = (Integer.parseInt(hex.substring(0,1), 16) * 0xF) min 255
        val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF) min 255
        val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF) min 255
        val a = (Integer.parseInt(hex.substring(3, 4), 16) * 0xF) min 255
        new Color(r,g,b,a)
      case 5 => //RGBAA
        val r = (Integer.parseInt(hex.substring(0,1), 16) * 0xF) min 255
        val g = (Integer.parseInt(hex.substring(1, 2), 16) * 0xF) min 255
        val b = (Integer.parseInt(hex.substring(2, 3), 16) * 0xF) min 255
        val a = Integer.parseInt(hex.substring(3, 5), 16)
        new Color(r,g,b,a)
      case 6 => //RRGGBB
        val r = Integer.parseInt(hex.substring(0, 2), 16)
        val g = Integer.parseInt(hex.substring(2, 4), 16)
        val b = Integer.parseInt(hex.substring(4, 6), 16)
        new Color(r,g,b)
      case 7 => //RRGGBBA
        val r = Integer.parseInt(hex.substring(0, 2), 16)
        val g = Integer.parseInt(hex.substring(2, 4), 16)
        val b = Integer.parseInt(hex.substring(4, 6), 16)
        val a = (Integer.parseInt(hex.substring(6, 7), 16) * 0xF) min 255
        new Color(r,g,b,a)
      case 8 => //RRGGBBAA
        val r = Integer.parseInt(hex.substring(0, 2), 16)
        val g = Integer.parseInt(hex.substring(2, 4), 16)
        val b = Integer.parseInt(hex.substring(4, 6), 16)
        val a = Integer.parseInt(hex.substring(6, 8), 16)
        new Color(r,g,b,a)
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
   * from:glyphs   => Will load all glyphs in file "./glyphs". In UTF-8!
   */
  val GlyphFileRegex = "from (\\w+)".r

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
        val font = Font.createFont(Font.TRUETYPE_FONT, fontFile.file)

        val hieroSettings = new HieroSettings()
        hieroSettings.setBold(params.contains("b"))
        hieroSettings.setItalic(params.contains("i"))
        hieroSettings.setFontSize(size)
        hieroSettings.setFontName(font.getFontName)
        hieroSettings.setPaddingTop(paddingTop)
        hieroSettings.setPaddingBottom(paddingBottom)
        hieroSettings.setPaddingLeft(paddingLeft)
        hieroSettings.setPaddingRight(paddingRight)

        if (params.contains("native")) {
          hieroSettings.setNativeRendering(true)
        } else {
          hieroSettings.setNativeRendering(false)
        }

        val effects = hieroSettings.getEffects.asInstanceOf[java.util.List[Effect]]

        params collectFirst {
          case OutlineRegex(width, hexColor, optJoin) =>
            val effect = new OutlineEffect(Integer.parseInt(width), parseHexColor(hexColor))
            if("bevel".equalsIgnoreCase(optJoin)){
              effect.setJoin(BasicStroke.JOIN_BEVEL)
            }else if("miter".equalsIgnoreCase(optJoin)){
              effect.setJoin(BasicStroke.JOIN_MITER)
            }else if("round".equalsIgnoreCase(optJoin)){
              effect.setJoin(BasicStroke.JOIN_ROUND)
            }
            Log.debug(Name, "Added outline effect: "+effect.getColor+" "+effect.getWidth+"px")
            effects.add(effect)
        }

        params collectFirst {
          case FGRegex(hexColor) =>
            Log.debug(Name, "Foreground color for font set. " + fontFile)
            parseHexColor(hexColor)
        } match {
          case Some(color) =>
            effects.add(new ColorEffect(color))
          case None =>
            effects.add(new ColorEffect())
        }

        val unicode = new UnicodeFont(fontFile.file.getAbsolutePath, hieroSettings)

        var glyphsAdded = false
        if (params.contains("ascii")) {
          unicode.addAsciiGlyphs()
          glyphsAdded = true
          Log.debug(Name, "ASCII glyphs added")
        }
        if (params.contains("nehe")) {
          unicode.addNeheGlyphs()
          glyphsAdded = true
          Log.debug(Name, "NEHE glyphs added")
        }

        for (p <- params) {
          p match {
            case GlyphRangeRegex(start, end) =>
              val from = start.toInt
              val to = end.toInt
              unicode.addGlyphs(from, to)
              glyphsAdded = true
              Log.debug(Name, s"Added glyphs from $from to $to.")
            case GlyphFileRegex(file) =>
              fontFile.parent.getChildFile(file) match {
                case Some(childFile) =>
                  val reader = Files.newReader(childFile.file, Charsets.UTF_8)
                  var line = reader.readLine()
                  while (line != null) {
                    unicode.addGlyphs(line)
                    line = reader.readLine()
                  }
                  reader.close()
                  fontFile.parent.removeChild(childFile)
                  glyphsAdded = true
                  Log.debug(Name, "Added glyphs from file. " + fontFile)
                case None =>
                  Log.warn(Name, "File to load glyphs from not found. " + fontFile.parent + " " + file)
              }
            case _ =>
          }
        }

        if (!glyphsAdded) {
          //Default
          unicode.addAsciiGlyphs()
          Log.debug(Name, "No glyphs specified. Thus adding ASCII glyphs.")
        }
        unicode.setGlyphPageWidth(2048) //Should be enough
        unicode.setGlyphPageHeight(2048)

        val hieroUtil = new BMFontUtil(unicode)

        val outputFolder = newFolder()
        val outputFile = new File(outputFolder, fontFile.name)

        hieroUtil.save(outputFile)

        val pageCount = unicode.getGlyphPages.size()
        if (pageCount == 0) {
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

        for (generatedJavaFile <- outputFolder.listFiles()) {
          if (Files.getFileExtension(generatedJavaFile.getName).equalsIgnoreCase("png")) {
            ImageUtil.clampImage(generatedJavaFile)
            for (backgroundColor <- bgColor) {
              ImageUtil.preBlendImage(generatedJavaFile, backgroundColor)
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
