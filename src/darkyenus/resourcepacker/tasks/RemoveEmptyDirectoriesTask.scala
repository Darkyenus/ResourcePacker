package darkyenus.resourcepacker.tasks

import com.esotericsoftware.minlog.Log
import darkyenus.resourcepacker.{ResourceDirectory, Task}

/**
 * Removes all empty directories that don't have `retain` flag.
 * @author Darkyen
 */
object RemoveEmptyDirectoriesTask extends Task {

  override def operate(emptyDirectory: ResourceDirectory): Boolean = {
    if (!emptyDirectory.hasChildren && !emptyDirectory.flags.contains("retain")) {
      Log.info(Name, "Empty directory removed. " + emptyDirectory)
      emptyDirectory.parent.removeChild(emptyDirectory)
      true
    } else false
  }
}
