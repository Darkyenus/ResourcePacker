package darkyenus.resourcepacker.tasks.densitypack

import java.awt.Color
import java.io.FileReader

import com.badlogic.gdx.utils.{Json, JsonReader}
import com.esotericsoftware.minlog.Log
import com.google.common.io.Files
import darkyenus.resourcepacker.tasks.{FlattenTask, PreBlendTask}
import darkyenus.resourcepacker.util.ImageUtil
import darkyenus.resourcepacker.{ResourceDirectory, ResourceFile, Task}
import org.apache.batik.transcoder.SVGAbstractTranscoder

/**
  * Packs all images in .pack. flagged directory using libGDX's texture packer and then flattens it.
  * Can also preblend all packed resources if supplied with .#RRGGBB. flag, see [[PreBlendTask]].
  *
  * If the directory contains pack.json file (name can contain flags),
  * it will be used in packing, as with default packing procedures.
  *
  * @author Darkyen
  */
object DensityPackTask extends Task {

  /**
    * Matches: #RRGGBB
    * Where RR (GG and BB) are hexadecimal digits.
    * Example:
    * #FF0056
    * to capture groups RR GG BB
    */
  val PreBlendRegex = "#([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])".r

  val ScalesRegex = "\\@([1-9]+[0-9]*)x".r

  val ScaledNameRegex = "(.+)\\@([1-9]+[0-9]*)x?".r

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

  private val json = new Json()
  private val jsonReader = new JsonReader

  override def operate(packDirectory: ResourceDirectory): Boolean = {
    if (packDirectory.flags.contains("densitypack")) {
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
      settings.alphaThreshold = 1
      settings.silent = false
      settings.ignoreBlankImages = false
      //settings.debug = true
      packDirectory.files.find(file => file.name == "pack" && file.extension == "json") match {
        case Some(packFile) =>
          json.readFields(settings, jsonReader.parse(new FileReader(packFile.file)))
          packDirectory.removeChild(packFile)
          if(Log.DEBUG)Log.debug(Name, "Json packer settings loaded. " + packFile.file.getAbsolutePath)
        case None =>
      }

      if(settings.duplicatePadding && (settings.paddingX < 2 && settings.paddingY < 2)){
        Log.warn(Name, "duplicatePadding is true, but padding is less than 2 so it won't have any effect")
      } else if(settings.duplicatePadding && (settings.paddingX < 2 || settings.paddingY < 2)){
        if(Log.DEBUG)Log.debug(Name,
          "duplicatePadding is true, but padding of one dimension is less than 2 so it won't have any effect "+
            "(paddingX = "+settings.paddingX+", paddingY = "+settings.paddingY+")")
      }

      settings.scales = (packDirectory.flags.collect {
        case ScalesRegex(scale) => scale.toInt
      }.toSet + 1).toSeq.sorted.toArray

      val packer = new TexturePacker(settings)
      for (image <- packDirectory.files if image.isImage || image.extension.equalsIgnoreCase("svg")) {
        var name = image.name
        var scale = 1

        name match {
          case ScaledNameRegex(trimmedName, scaleFactor) =>
            name = trimmedName
            scale = scaleFactor.toInt
          case _ =>
        }

        val imageSource = packer.addImage(image.file, name, -1, scale, image.flags.contains("9"))

        image.flags.collectFirst {
          case PixelSizePattern(width, height) =>
            imageSource.rasterizationHints.put(SVGAbstractTranscoder.KEY_WIDTH, width.toInt.toFloat.asInstanceOf[java.lang.Float])
            imageSource.rasterizationHints.put(SVGAbstractTranscoder.KEY_HEIGHT, height.toInt.toFloat.asInstanceOf[java.lang.Float])
          case PixelWidthPattern(width) =>
            imageSource.rasterizationHints.put(SVGAbstractTranscoder.KEY_WIDTH, width.toInt.toFloat.asInstanceOf[java.lang.Float])
          case PixelHeightPattern(height) =>
            imageSource.rasterizationHints.put(SVGAbstractTranscoder.KEY_HEIGHT, height.toInt.toFloat.asInstanceOf[java.lang.Float])
        }

        if(Log.DEBUG)Log.debug(Name, s"Image added to pack. " + image)
        packDirectory.removeChild(image)
      }

      val atlasName = packDirectory.name
      val outputFolder = newFolder()
      packer.pack(outputFolder, atlasName)

      val preBlendColor = packDirectory.flags collectFirst {
        case PreBlendRegex(r, g, b) =>
          if(Log.DEBUG)Log.debug(Name, "Blending color for atlas set. " + atlasName)
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
