package com.darkyen.resourcepacker.tasks

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonReader
import com.darkyen.resourcepacker.Resource
import com.darkyen.resourcepacker.Resource.Companion.isImage
import com.darkyen.resourcepacker.Resource.ResourceDirectory
import com.darkyen.resourcepacker.Task
import com.darkyen.resourcepacker.util.preBlendImage
import com.darkyen.resourcepacker.util.tools.texturepacker.TexturePacker
import com.esotericsoftware.minlog.Log
import com.google.common.io.Files
import java.awt.Color
import java.io.FileReader
import javax.imageio.ImageIO

/**
 * Packs all images in .pack. flagged directory using libGDX's texture packer and then flattens it.
 * Can also pre-blend all packed resources if supplied with .#RRGGBB. flag, see [PreBlendTask].
 *
 * If the directory contains pack.json file (name can contain flags),
 * it will be used in packing, as with default packing procedures.
 *
 * @author Darkyen
 */
object PackTask : Task() {

    /**
     * Matches: #RRGGBB
     * Where RR (GG and BB) are hexadecimal digits.
     * Example:
     * #FF0056
     * to capture groups RR GG BB
     */
    val PreBlendRegex = Regex("#([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])([0-9A-Fa-f][0-9A-Fa-f])")

    private val json = Json()
    private val jsonReader = JsonReader()

    override fun operate(directory: ResourceDirectory): Boolean {
        if (!directory.flags.contains("pack")) {
            return false
        }

        val settings = TexturePacker.Settings()
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

        for (packFile in directory.files) {
            if (packFile.name == "pack" && packFile.extension == "json") {
                json.readFields(settings, jsonReader.parse(FileReader(packFile.file)))
                directory.removeChild(packFile)
                if (Log.DEBUG) Log.debug(Name, "Json packer settings loaded. " + packFile.file.canonicalPath)
                break
            }
        }

        if (settings.duplicatePadding && (settings.paddingX < 2 && settings.paddingY < 2)) {
            Log.warn(Name, "duplicatePadding is true, but padding is less than 2 so it won't have any effect")
        } else if (settings.duplicatePadding && (settings.paddingX < 2 || settings.paddingY < 2)) {
            if (Log.DEBUG) Log.debug(Name,
                    "duplicatePadding is true, but padding of one dimension is less than 2 so it won't have any effect " +
                            "(paddingX = " + settings.paddingX + ", paddingY = " + settings.paddingY + ")")
        }

        val packer = TexturePacker(settings)
        directory.forEachFile(filter = {it.isImage()}) { image ->
            val bufferedImage = ImageIO.read(image.file)
            if (bufferedImage == null) {
                Log.error(Name, "Image could not be loaded. " + image)
            } else {
                packer.addImage(bufferedImage, image.name + (if (image.flags.contains("9")) ".9" else ""))
                if (Log.DEBUG) Log.debug(Name, "Image added to pack. $image")
            }
            directory.removeChild(image)
        }

        val atlasName = directory.name
        val outputFolder = newFolder()
        packer.pack(outputFolder, atlasName)

        val preBlendColor = directory.flags.matchFirst(PreBlendRegex) { (r, g, b) ->
            if (Log.DEBUG) Log.debug(Name, "Blending color for atlas set. " + atlasName)
            Color(Integer.parseInt(r, 16), Integer.parseInt(g, 16), Integer.parseInt(b, 16))
        }

        for (outputJavaFile in outputFolder.listFiles()) {
            if (Files.getFileExtension(outputJavaFile.name).equals("png", ignoreCase = true)) {
                if (preBlendColor != null) {
                    preBlendImage(outputJavaFile, preBlendColor)
                }
            }
            directory.parent.addChild(Resource.ResourceFile(outputJavaFile, directory.parent))
        }
        FlattenTask.flatten(directory)
        Log.info(Name, "Image atlas packed and directory has been flattened. $atlasName $directory")
        return true
    }
}
