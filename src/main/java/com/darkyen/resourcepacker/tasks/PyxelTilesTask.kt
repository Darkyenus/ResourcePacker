package com.darkyen.resourcepacker.tasks

import com.badlogic.gdx.utils.StreamUtils
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Extracts tiles from .pyxel archives tagged with .tiles.
 *
 * @author Darkyen
 */
object PyxelTilesTask : Task() {

    val TileExtension = ".png"

    /** Do your work here.
     * Called once for each file remaining in virtual working filesystem, per run.
     * @return whether the operation did something or not */
    override fun operate(file: ResourceFile): Boolean {
        if (file.flags.contains("tiles")) {
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

                file.parent.addChild(resultFile)

                id += 1
                entry = tile(id)
            }
            zip.close()

            file.removeFromParent()
            return true
        }

        return false
    }
}
