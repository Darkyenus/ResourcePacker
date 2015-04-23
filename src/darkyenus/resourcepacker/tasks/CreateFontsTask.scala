package darkyenus.resourcepacker.tasks

import java.awt.{Color, Font}
import java.io.File

import com.badlogic.gdx.tools.hiero.BMFontUtil
import com.badlogic.gdx.tools.hiero.unicodefont.effects.{ColorEffect, Effect}
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

  val PaddingRegex = "p(t|b|l|r|)(\\d+)".r
  /* Examples:
   * pt56     => Padding left 56
   * p89      => Padding everywhere 89
   */
  val SizeRegex = "(\\d+)".r
  /*
  Example:
  14          => Size 14
   */
  val GlyphRangeRegex = "(\\d+)-(\\d+)".r
  /* Example:
     56-67     => Glyphs 56 to 67 should be added
   */
  val GlyphFileRegex = "from (\\w+)".r
  /* Example:
      from:glyphs   => Will load all glyphs in file "./glyphs". In UTF-8!
   */

  /** Matches bg#RRGGBBAA colors for background. Default is Transparent. */
  val BGRegex = "bg#([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])".r
  /** Matches bg#RRGGBBAA colors for foreground (color of font). Default is White. */
  val FGRegex = "fg#([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])".r


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
        Log.error(Name, "Define size of font. (example.14.ttf) " + fontFile)
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
        effects.add(new ColorEffect())

        val unicode = new UnicodeFont(fontFile.file.getAbsolutePath, hieroSettings)

        var glyphsAdded = false
        if (params.contains("ascii")) {
          unicode.addAsciiGlyphs()
          glyphsAdded = true
        }
        if (params.contains("nehe")) {
          unicode.addNeheGlyphs()
          glyphsAdded = true
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
          case BGRegex(r, g, b, a) =>
            Log.debug(Name, "Background color for font set. " + fontFile)
            new Color(Integer.parseInt(r, 16), Integer.parseInt(g, 16), Integer.parseInt(b, 16), Integer.parseInt(a, 16))
        }
        val fgColor = params collectFirst {
          case FGRegex(r, g, b, a) =>
            Log.debug(Name, "Foreground color for font set. " + fontFile)
            new Color(Integer.parseInt(r, 16), Integer.parseInt(g, 16), Integer.parseInt(b, 16), Integer.parseInt(a, 16))
        }

        for (generatedJavaFile <- outputFolder.listFiles()) {
          if (Files.getFileExtension(generatedJavaFile.getName).equalsIgnoreCase("png")) {
            ImageUtil.clampImage(generatedJavaFile)
            for (foregroundColor <- fgColor) {
              ImageUtil.multiplyImage(generatedJavaFile, foregroundColor)
            }
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
