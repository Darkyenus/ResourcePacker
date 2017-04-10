package darkyenus.resourcepacker

import java.io.File

import com.esotericsoftware.minlog.Log
import com.google.common.io.Files

import scala.util.Random

/**
 * Class that keeps all working files in check, creates new ones and throws old ones away.
 *
 * @author Darkyen
 */
class OperationJanitor(workingRootProvider: WorkingRootProvider) {

  val workingRoot = workingRootProvider.getTemporaryRoot(this)

  /** Who uses it last cleans it. */
  private val TMP = new StringBuilder

  def createTempFile(taskName: String, fileName:String, file: ResourceFile, extension: String): File = {
    var result: File = null
    do {
      TMP.append(fileName).append('.')

      TMP.append(taskName).append("-f-")
      fillWithRandomText(TMP)
      TMP.append('.')
      file.flags.addString(TMP, ".")
      TMP.append('.').append(if (extension == null) file.extension else extension)
      result = new File(workingRoot, TMP.toString())
      if(Log.DEBUG)Log.debug("OperationJanitor","Trying to create file in \""+workingRoot.getCanonicalPath+"\" called \""+TMP+"\".")
      TMP.clear()
    } while (result.exists())
    result
  }

  def createTempFile(taskName: String, fileName:String, extension: String): File = {
    var result: File = null
    do {
      TMP.append(fileName).append('.')

      TMP.append(taskName).append("-f-")
      fillWithRandomText(TMP)
      TMP.append('.')
      if(extension != null){
        TMP.append(extension)
      }
      result = new File(workingRoot, TMP.toString())
      if(Log.DEBUG)Log.debug("OperationJanitor","Trying to create file from scratch in \""+workingRoot.getCanonicalPath+"\" called \""+TMP+"\".")
      TMP.clear()
    } while (result.exists())
    result
  }

  def createTempDirectory(taskName: String): File = {
    var result: File = null
    do {
      TMP.append(taskName).append("-d-")
      fillWithRandomText(TMP)
      result = new File(workingRoot, TMP.toString())
      TMP.clear()
    } while (result.exists())
    result.mkdirs()
    result
  }

  private val RandomCharacters = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')

  private def fillWithRandomText(b: StringBuilder, amount: Int = 8): Unit = {
    for (_ <- 0 until amount) {
      b.append(RandomCharacters(Random.nextInt(RandomCharacters.length)))
    }
  }

  def clearFolder(dir: File, deleteThis: Boolean = false) {
    if (dir.isDirectory) {
      for (file <- dir.listFiles()) {
        if (file.isFile) {
          if (!file.delete()) {
            Log.warn("OperationJanitor", s"File ${file.getPath} not deleted.")
          }
        } else if (file.isDirectory) {
          clearFolder(file, deleteThis = true)
        }
      }
      if (deleteThis && !dir.delete()) {
        Log.warn("OperationJanitor", s"Directory ${dir.getPath} not deleted.")
      }
    } else if (dir.isFile) {
      Log.warn("OperationJanitor", s"Directory ${dir.getPath} is actually a file.")
    }
  }

  def dispose(): Unit = {
    if (workingRootProvider.shouldDeleteRoot) {
      clearFolder(workingRoot, deleteThis = true)
    }
  }
}

trait WorkingRootProvider {
  def getTemporaryRoot(operationJanitor: OperationJanitor): File

  val shouldDeleteRoot: Boolean
}

object TemporaryWorkingRootProvider extends WorkingRootProvider {
  override def getTemporaryRoot(operationJanitor: OperationJanitor): File = Files.createTempDir()

  override val shouldDeleteRoot: Boolean = true
}

class LocalWorkingRootProvider(workingRoot: File) extends WorkingRootProvider {

  override def getTemporaryRoot(operationJanitor: OperationJanitor): File = {
    workingRoot.mkdirs()
    operationJanitor.clearFolder(workingRoot)
    workingRoot
  }

  override val shouldDeleteRoot: Boolean = false
}