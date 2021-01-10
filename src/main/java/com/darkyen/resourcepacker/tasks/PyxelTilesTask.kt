package com.darkyen.resourcepacker.tasks

import com.badlogic.gdx.utils.StreamUtils
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.darkyen.resourcepacker.image.createImage
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Extracts tiles from .pyxel archives tagged with .tiles. and .ui-tiles.
 *
 * .ui-tiles. checks the image and if it appears to be a ninepatch, append .9. to it.
 */
object PyxelTilesTask : Task() {

    val TileExtension = ".png"

    /** Do your work here.
     * Called once for each file remaining in virtual working filesystem, per run.
     * @return whether the operation did something or not */
    override fun operate(file: ResourceFile): Boolean {
        val uiTiles = file.flags.contains("ui-tiles")
        if (!file.flags.contains("tiles") && !uiTiles) {
            return false
        }

        val zip = ZipFile(file.file)
        fun tile(id: Int): ZipEntry? = zip.getEntry("tile$id$TileExtension")
        val tileStoreFolder = newFolder()

        val copyBuffer = ByteArray(StreamUtils.DEFAULT_BUFFER_SIZE)

        var id = 0
        var entry: ZipEntry? = tile(id)
        while (entry != null) {
            val resultFile = File(tileStoreFolder, "${file.name}$id$TileExtension")
            val entryInput = zip.getInputStream(entry)

            val resultFileOutput = FileOutputStream(resultFile, false)
            StreamUtils.copyStream(entryInput, resultFileOutput, copyBuffer)
            StreamUtils.closeQuietly(entryInput)
            StreamUtils.closeQuietly(resultFileOutput)

            val resource = file.parent.addChild(resultFile)
            if (resource != null && uiTiles && resource is ResourceFile) {
                val image = resource.createImage()
                if (image != null && image.couldBeNinepatch()) {
                    resource.flags.add("9")
                }
            }

            id += 1
            entry = tile(id)
        }
        zip.close()

        file.removeFromParent()
        return true
    }
}
