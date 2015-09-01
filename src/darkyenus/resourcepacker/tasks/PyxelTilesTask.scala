package darkyenus.resourcepacker.tasks

import java.io.{File, FileOutputStream}
import java.util.zip.{ZipEntry, ZipFile}

import com.badlogic.gdx.utils.StreamUtils
import darkyenus.resourcepacker.{ResourceFile, Task}

/**
 * Extracts tiles from .pyxel archives tagged with .tiles.
 *
 * @author Darkyen
 */
object PyxelTilesTask extends Task {

  val TileExtension = ".png"

  /** Do your work here.
    * Called once for each file remaining in virtual working filesystem, per run.
    * @return whether the operation did something or not */
  override def operate(file: ResourceFile): Boolean = {
    if(file.flags.contains("tiles")){
      val zip = new ZipFile(file.file)
      def tile(id:Int):ZipEntry = zip.getEntry("tile"+id+TileExtension)
      val tileStoreFolder = newFolder()

      val copyBuffer = new Array[Byte](StreamUtils.DEFAULT_BUFFER_SIZE)

      var id = 0
      var entry:ZipEntry = tile(id)
      while(entry != null) {
        val resultFile = new File(tileStoreFolder, file.name + id + TileExtension)
        val entryInput = zip.getInputStream(entry)

        val resultFileOutput = new FileOutputStream(resultFile, false)
        StreamUtils.copyStream(entryInput, resultFileOutput, copyBuffer)
        StreamUtils.closeQuietly(entryInput)
        StreamUtils.closeQuietly(resultFileOutput)

        file.parent.addChild(resultFile)

        id += 1
        entry = tile(id)
      }
      zip.close()

      file.removeFromParent()
      true
    }else false
  }
}
