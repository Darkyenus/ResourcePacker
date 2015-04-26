package darkyenus.resourcepacker

import java.io.File

import com.esotericsoftware.minlog.Log
import tasks.DefaultTasks

/**
 * Bureaucratic part. Assigning what should run on what and when. Used by launcher.
 *
 * @author Darkyen
 */
object ResourcePacker {

  private def createTree(root:File):ResourceDirectory = {
    if(!root.isDirectory){
      Log.error("ResourcePacker",s"${root.getCanonicalPath} is not a directory.")
      return null
    }
    val result = new ResourceDirectory(root,null)
    result.parent = result
    result.create()
    result
  }

  private def prepareOutputDirectory(janitor: OperationJanitor, to:File){
    janitor.clearFolder(to)
    if(!to.exists() && !to.mkdirs()){
      Log.warn("ResourcePacker","Output directory at \""+to.getCanonicalPath+"\" could not be created. Assuming it's fine.")
    }
  }

  def apply(packingOperation: PackingOperation): Unit ={
    val startTime = System.currentTimeMillis()
    val root = createTree(packingOperation.from)
    if(root == null)return
    Log.info("ResourcePacker","Starting packing operation from \""+packingOperation.from.getCanonicalPath+"\" to \"" +packingOperation.to.getCanonicalPath+"\"")

    if(root.flags.nonEmpty)Log.warn("ResourcePacker","Flags of root will not be processed.")

    val janitor = new OperationJanitor(packingOperation.workingRootProvider)

    prepareOutputDirectory(janitor, packingOperation.to)

    for(task <- packingOperation.tasks){
      task.initializeForOperation(janitor)
    }

    for(setting <- packingOperation.settings){
      setting.activate()
    }

    for(task <- packingOperation.tasks){
      if(task.repeating){
        var times = 0
        while(task.operate()){
          times += 1
        }
        while(root.applyTask(task)){
          times += 1
        }
        Log.debug("ResourcePacker","Task "+task.Name+" run "+times+" times")
      }else{
        val subMessage = if(task.operate()) "(did run in operate(void))" else "(did not run in operate(void))"
        if(root.applyTask(task))Log.debug("ResourcePacker","Task "+task.Name+" finished and run " + subMessage)
        else Log.debug("ResourcePacker","Task "+task.Name+" finished but didn't run "+subMessage)
      }
    }

    for(setting <- packingOperation.settings){
      setting.reset()
    }

    root.copyYourself(packingOperation.to,root = true)

    janitor.dispose()
    Log.info("ResourcePacker","Packing operation done (in "+((System.currentTimeMillis() - startTime)/1000f).formatted("%.2f")+"s)")
  }
}

class PackingOperation (val from:File, val to:File, val settings:Seq[Setting[_]] = Seq(), val tasks:Seq[Task] = DefaultTasks , val workingRootProvider: WorkingRootProvider = TemporaryWorkingRootProvider)

/**
 * Tasks can create immutable instances of this.
 * User then can create setting tuple from it:
 * @example
 * {{{
 * //In Task
 * val PageSize = new SettingKey[Int]("PageSize",1024,"Page size is the size of the page")
 *
 * //In creating packing operation
 * ... settings = Seq(PageSize := 56, SomethingElse := true, ...) ...
 * }}}
 * @author Darkyen
 */
final class SettingKey[T](val name:String, defaultValue:T, val help:String = "") {
  def :=(content:T):Setting[T] = new Setting(this,content)

  var activeValue:T = defaultValue

  def get():T = activeValue

  def reset(): Unit ={
    activeValue = defaultValue
  }
}

/**
 * Created by calling [[SettingKey.:=()]] and fed into the PackingOperation
 *
 * @author Darkyen
 */
final class Setting[T](val key: SettingKey[T], val value:T){
  def activate(): Unit ={
    key.activeValue = value
  }

  def reset(): Unit ={
    key.reset()
  }
}