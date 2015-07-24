package darkyenus.resourcepacker.tasks

import com.esotericsoftware.minlog.Log
import darkyenus.resourcepacker.{ResourceDirectory, Task}

/**
 * Flattens directories marked with `flatten`.
 * @author Darkyen
 */
object FlattenTask extends Task {

  override val repeating: Boolean = true

  override def operate(directory: ResourceDirectory): Boolean = {
    if (directory.flags.contains("flatten")) {
      flatten(directory)
      Log.info(Name, "Directory flattened. (" + directory + ")")
      true
    } else false
  }

  def flatten(directory: ResourceDirectory) {
    //Grandparent will no longer acknowledge this child and take all his children. Harsh.
    val grandparent = directory.parent
    grandparent.removeChild(directory)
    directory.children.foreach(child => {
      directory.removeChild(child)
      grandparent.addChild(child)
    })
  }
}
