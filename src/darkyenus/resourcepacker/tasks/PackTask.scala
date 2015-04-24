package darkyenus.resourcepacker.tasks

import java.awt.Color
import java.io.FileReader
import javax.imageio.ImageIO

import com.badlogic.gdx.tools.texturepacker.TexturePacker
import com.badlogic.gdx.utils.{Json, JsonReader}
import com.esotericsoftware.minlog.Log
import com.google.common.io.Files
import darkyenus.resourcepacker.util.ImageUtil
import darkyenus.resourcepacker.{ResourceDirectory, ResourceFile, Task}

/**
 * Packs all images in .pack. flagged directory using libGDX's texture packer and then flattens it.
 * Can also preblend all packed resources if supplied with .#RRGGBB. flag, see [[PreBlendTask]].
 *
 * If the directory contains pack.json file (name can contain flags),
 * it will be used in packing, as with default packing procedures.
 *
 * @author Darkyen
 */
object PackTask extends Task {

  /**
   * Matches: #RRGGBB
   * Where RR (GG and BB) are hexadecimal digits.
   * Example:
   * #FF0056
   * to capture groups RR GG BB
   */
  val PreBlendRegex = "#([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])".r

  private val json = new Json()
  private val jsonReader = new JsonReader

  override def operate(packDirectory: ResourceDirectory): Boolean = {
    if (packDirectory.flags.contains("pack")) {
      val settings = new TexturePacker.Settings()
      settings.limitMemory = false
      settings.useIndexes = false
      settings.filterMag = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
      settings.filterMin = settings.filterMag
      settings.pot = true //Seems that it is still better for performance and whatnot
      settings.maxWidth = 2048
      settings.maxHeight = settings.maxWidth
      settings.duplicatePadding = true
      settings.bleed = true
      settings.stripWhitespaceX = true
      settings.stripWhitespaceY = true
      settings.alphaThreshold = 0
      packDirectory.files.find(file => file.name == "pack" && file.extension == "json") match {
        case Some(packFile) =>
          json.readFields(settings, jsonReader.parse(new FileReader(packFile.file)))
          packDirectory.removeChild(packFile)
          Log.info(Name, "Json packer settings loaded. " + packFile.file.getAbsolutePath)
        case None =>
      }
      val packer = new TexturePacker(settings)
      for (image <- packDirectory.files if image.isImage) {
        val bufferedImage = ImageIO.read(image.file)
        if (bufferedImage == null) {
          Log.error(Name, "Image could not be loaded. " + image)
        } else {
          packer.addImage(bufferedImage, image.name + (if (image.flags.contains("9")) ".9" else ""))
          Log.info(Name, s"Image added to pack. " + image)
        }
        packDirectory.removeChild(image)
      }

      val atlasName = packDirectory.name
      val outputFolder = newFolder()
      packer.pack(outputFolder, atlasName)

      val preBlendColor = packDirectory.flags collectFirst {
        case PreBlendRegex(r, g, b) =>
          Log.info(Name, "Blending color for atlas set. " + atlasName)
          new Color(Integer.parseInt(r, 16), Integer.parseInt(g, 16), Integer.parseInt(b, 16))
      }

      for (outputJavaFile <- outputFolder.listFiles()) {
        if (Files.getFileExtension(outputJavaFile.getName).equalsIgnoreCase("png")) {
          for (color <- preBlendColor) {
            ImageUtil.preBlendImage(outputJavaFile, color)
          }
        }
        packDirectory.parent.addChild(new ResourceFile(outputJavaFile, packDirectory.parent))
      }
      FlattenTask.flatten(packDirectory)
      Log.info(Name, "Image atlas packed and directory has been flattened. " + atlasName + " " + packDirectory)
      true
    } else false
  }
}
