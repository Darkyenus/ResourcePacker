package darkyenus.resourcepacker.tasks

import com.esotericsoftware.minlog.Log
import darkyenus.resourcepacker.{ResourceFile, ResourceDirectory, Task}

/**
 * Removes all files and directories marked with `.ignore.`
 * @author Darkyen
 */
object IgnoreTask extends Task {

  override def operate(file: ResourceFile): Boolean = {
    if(file.flags.contains("ignore")){
      file.removeFromParent()
      Log.info(Name, "File ignored. (" + file + ")")
      true
    }else false
  }

  override def operate(directory: ResourceDirectory): Boolean = {
    if(directory.flags.contains("ignore")){
      directory.parent.removeChild(directory)
      Log.info(Name, "Directory ignored. (" + directory + ")")
      true
    }else false
  }
}
