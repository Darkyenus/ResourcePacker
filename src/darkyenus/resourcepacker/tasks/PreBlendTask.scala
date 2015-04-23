package darkyenus.resourcepacker.tasks

import java.awt.Color

import com.google.common.io.Files
import darkyenus.resourcepacker.util.ImageUtil
import darkyenus.resourcepacker.{ResourceFile, Task}

/**
 * Renders image onto background of given color.
 *
 * {{{
 *   Input flags:
 *
 *   .#RRGGBB.
 *
 *   CC colors are in hexa
 * }}}
 *
 * @example
 * `.#FFFFFF.` would put a white background on the image
 *
 * @author Darkyen
 */
object PreBlendTask extends Task {

  /**
   * Matches: #RRGGBB
   * Where RR (GG and BB) are hexadecimal digits.
   * Example:
   * #FF0056
   * to capture groups RR GG BB
   */
  val PreBlendRegex = "#([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])".r

  /** Do your work here.
    * @return whether the operation did something or not */
  override def operate(file: ResourceFile): Boolean = {
    if (file.isImage) {
      file.flags collectFirst {
        case PreBlendRegex(r, g, b) =>
          val color = new Color(Integer.parseInt(r, 16), Integer.parseInt(g, 16), Integer.parseInt(b, 16))
          val output = newFile(file)
          Files.copy(file.file, output)
          ImageUtil.preBlendImage(output, color, ninepatch = file.flags.contains("9"))
          file.file = output
          return true
      }
    }
    false
  }
}
