package darkyenus.resourcepacker

import java.io.File

/**
 * Base class for all tasks.
 *
 * Multithreading not supported.
 *
 * If your implementation keeps any state, override initialize() to also reset your state.
 *
 * @author Darkyen
 */
abstract class Task {

  private var janitor:OperationJanitor = null

  final def initializeForOperation(janitor: OperationJanitor): Unit ={
    this.janitor = janitor
  }

  val Name = getClass.getSimpleName.stripSuffix("$")

  /**
   * Creates a new file based on given existing file.
   * It will have the same characteristics, extension (unless specified otherwise)
   * and will not exist. It can be used as a drop-in replacement for active ResourceFile.file.
   *
   * @return non existent file, ready to be created
   */
  final def newFile(basedOn:ResourceFile, extension:String = null): File ={
    janitor.createTempFile(Name,basedOn, extension)
  }

  final def newFolder():File = {
    janitor.createTempDirectory(Name)
  }


  /** Do your work here.
    * @return whether the operation did something or not */
  def operate(file: ResourceFile):Boolean = false

  /** Do your work here.
    * @return whether the operation did something or not */
  def operate(directory: ResourceDirectory):Boolean = false

  /** Repeating tasks will run over and over until they don't success anymore on anything. */
  val repeating = false

}
