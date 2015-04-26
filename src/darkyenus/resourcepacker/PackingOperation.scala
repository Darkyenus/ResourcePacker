package darkyenus.resourcepacker

import java.io.File

import com.esotericsoftware.minlog.Log
import darkyenus.resourcepacker.tasks.DefaultTasks

/**
 * Main type of `Operation`. Takes tasks and executes them on given inputs with given settings.
 *
 * @author Darkyen
 */
class PackingOperation(val from: File, val to: File, val settings: Seq[Setting[_]] = Seq(), val tasks: Seq[Task] = DefaultTasks, val workingRootProvider: WorkingRootProvider = TemporaryWorkingRootProvider) extends (() => Unit) {
  private def createTree(root: File): ResourceDirectory = {
    if (!root.isDirectory) {
      Log.error("ResourcePacker", s"${root.getCanonicalPath} is not a directory.")
      return null
    }
    val result = new ResourceDirectory(root, null)
    result.parent = result
    result.create()
    result
  }

  private def prepareOutputDirectory(janitor: OperationJanitor, to: File) {
    janitor.clearFolder(to)
    if (!to.exists() && !to.mkdirs()) {
      Log.warn("ResourcePacker", "Output directory at \"" + to.getCanonicalPath + "\" could not be created. Assuming it's fine.")
    }
  }

  private def logVirtualTreeAfter(after:Task, tree:ResourceDirectory): Unit ={
    if(Log.DEBUG){
      Log.debug("PackingOperation","After running "+after.Name+", virtual filesystem looks like this:")
      val SB = new StringBuilder
      SB.append('\n') //Logger already prints something on the line, so this makes it even
      tree.toPrettyString(SB,1)
      Log.debug(SB.toString())
    }
  }

  /**
   * Does the actual work. Should be called only by launcher that has created a necessary context for tasks.
   */
  def apply(): Unit = {
    val startTime = System.currentTimeMillis()
    val root = createTree(this.from)
    if (root == null) return
    Log.info("ResourcePacker", "Starting packing operation from \"" + this.from.getCanonicalPath + "\" to \"" + this.to.getCanonicalPath + "\"")

    if (root.flags.nonEmpty) Log.warn("ResourcePacker", "Flags of root will not be processed.")

    val janitor = new OperationJanitor(this.workingRootProvider)

    prepareOutputDirectory(janitor, this.to)

    for (task <- this.tasks) {
      task.initializeForOperation(janitor)
    }

    for (setting <- this.settings) {
      setting.activate()
    }

    for (task <- this.tasks) {
      if (task.repeating) {
        var times = 0
        while (task.operate()) {
          times += 1
        }
        while (root.applyTask(task)) {
          logVirtualTreeAfter(task,root)
          times += 1
        }
        Log.debug("ResourcePacker", "Task " + task.Name + " run " + times + " times")
      } else {
        val subMessage = if (task.operate()) "(did run in operate(void))" else "(did not run in operate(void))"
        if (root.applyTask(task)) {
          logVirtualTreeAfter(task,root)
          Log.debug("ResourcePacker", "Task " + task.Name + " finished and run " + subMessage)
        } else Log.debug("ResourcePacker", "Task " + task.Name + " finished but didn't run " + subMessage)
      }
    }

    for (setting <- this.settings) {
      setting.reset()
    }

    root.copyYourself(this.to, root = true)

    janitor.dispose()
    Log.info("ResourcePacker", "Packing operation done (in " + ((System.currentTimeMillis() - startTime) / 1000f).formatted("%.2f") + "s)")
  }
}
