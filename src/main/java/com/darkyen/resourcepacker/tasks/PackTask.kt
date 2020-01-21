package com.darkyen.resourcepacker.tasks

import com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonReader
import com.darkyen.resourcepacker.Resource
import com.darkyen.resourcepacker.Resource.ResourceDirectory
import com.darkyen.resourcepacker.Task
import com.darkyen.resourcepacker.image.createImage
import com.darkyen.resourcepacker.isImage
import com.darkyen.resourcepacker.util.texturepacker.MultiScaleTexturePacker
import com.darkyen.resourcepacker.util.texturepacker.MultiScaleTexturePacker.Settings
import com.esotericsoftware.minlog.Log
import java.io.FileReader

/**
 * Packs all images in .pack. flagged directory using libGDX's texture packer and then flattens it.
 *
 * If the directory contains pack.json file (name can contain flags),
 * it will be used in packing, as with default packing procedures.
 *
 * Can generate multiple atlas image files with different, Apple-like densities, that is, with @2x scheme.
 * Files with @Nx (where N is scale level) are assigned to that level.
 * Missing density levels are created from existing ones, either by up/downscaling or by rasterization with different dimensions.
 * Automatically rasterizes .svg files, flags from RasterizeTask can be used for specifying scale.
 *
 * Generates only one atlas file and (potentially) multiple image files.
 * Therefore, all images are packed to the same locations, only all coordinates are multiplied by N.
 * This allows for simple runtime density switching.
 *
 * Scale level 1 is specified automatically, others (usually 2) can be added with @Nx flag (@2x in case of level 2).
 * Note that if the level is not a power of two (for example 3), `pot` setting must be set to false (default is true),
 * otherwise the level will be ignored and warning will be emitted.
 *
 * @author Darkyen
 */
object PackTask : Task() {

    private val ScalesRegex = Regex("@([1-9]+[0-9]*)x")

    private val ScaledNameRegex = Regex("(.+)@([1-9]+[0-9]*)x?")

    private val json = Json()
    private val jsonReader = JsonReader()

    override fun operate(directory: ResourceDirectory): Boolean {
        if (!directory.flags.contains("pack")) {
            return false
        }

        val settings = Settings()
        settings.limitMemory = false
        settings.useIndexes = false
        settings.filterMag = Linear
        settings.filterMin = settings.filterMag
        settings.pot = true //Seems that it is still better for performance and whatnot
        settings.maxWidth = 2048
        settings.maxHeight = settings.maxWidth
        settings.duplicatePadding = true
        settings.bleed = true
        settings.stripWhitespaceX = true
        settings.stripWhitespaceY = true
        settings.alphaThreshold = 0
        settings.ignoreBlankImages = false
        //settings.debug = true
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

        val scales = com.badlogic.gdx.utils.IntArray()
        scales.add(1)
        for (flag in directory.flags) {
            val scaleMatch = ScalesRegex.matchEntire(flag) ?: continue
            val scale = scaleMatch.groupValues[1].toInt()
            if (!scales.contains(scale)) {
                scales.add(scale)
            }
        }
        scales.sort()
        settings.scales = scales.toArray()

        val packer = MultiScaleTexturePacker(settings)
        directory.forEachFile(filter = { it.isImage() }) { image ->
            var name = image.name
            var scale = 1

            val scaledNameMatch = ScaledNameRegex.matchEntire(name)
            if (scaledNameMatch != null) {
                name = scaledNameMatch.groupValues[1]
                scale = scaledNameMatch.groupValues[2].toInt()
            }

            packer.addImage(name, -1, scale, image.createImage())

            if (Log.DEBUG) Log.debug(Name, "Image added to pack. $image")
            directory.removeChild(image)
        }

        val atlasName = directory.name
        val outputFolder = newFolder()
        packer.pack(outputFolder, atlasName)


        for (outputJavaFile in outputFolder.listFiles() ?: emptyArray()) {
            directory.parent.addChild(Resource.ResourceFile(outputJavaFile, directory.parent))
        }
        FlattenTask.flatten(directory)
        Log.info(Name, "Image atlas packed and directory has been flattened. $atlasName $directory")
        return true
    }
}
