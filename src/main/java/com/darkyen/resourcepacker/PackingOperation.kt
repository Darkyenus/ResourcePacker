package com.darkyen.resourcepacker

import com.darkyen.resourcepacker.tasks.DefaultTasks
import com.esotericsoftware.minlog.Log
import java.io.File

/**
 * Main type of `Operation`. Takes tasks and executes them on given inputs with given settings.
 *
 * @author Darkyen
 */
class PackingOperation(val from: File, val to: File,
                       val settings: List<Setting<*>> = emptyList(),
                       val tasks: List<Task> = DefaultTasks,
                       val workingRootProvider: WorkingRootProvider = TemporaryWorkingRootProvider) : ()->Unit {

    private fun createTree(root: File): Resource.ResourceDirectory? {
        if (!root.isDirectory) {
            Log.error("ResourcePacker", "${root.canonicalPath} is not a directory.")
            return null
        }
        val result = Resource.ResourceDirectory(root, null)
        result.parent = result
        result.create()
        return result
    }

    private fun prepareOutputDirectory(janitor: OperationJanitor, to: File) {
        janitor.clearFolder(to)
        if (!to.exists() && !to.mkdirs()) {
            Log.warn("ResourcePacker", "Output directory at \"" + to.canonicalPath + "\" could not be created. Assuming it's fine.")
        }
    }

    private fun logVirtualTreeAfter(after: Task, tree: Resource.ResourceDirectory) {
        if (Log.DEBUG) {
            Log.debug("PackingOperation", "After running " + after.Name + ", virtual filesystem looks like this:")
            val sb = StringBuilder()
            sb.append('\n') //Logger already prints something on the line, so this makes it even
            tree.toPrettyString(sb, 1)
            Log.debug(sb.toString())
        }
    }

    /**
     * Does the actual work. Should be called only by launcher that has created a necessary context for tasks.
     */
    override fun invoke() {
        val startTime = System.currentTimeMillis()
        val root = createTree(this.from) ?: return
        Log.info("ResourcePacker", "Starting packing operation from \"" + this.from.getCanonicalPath() + "\" to \"" + this.to.getCanonicalPath() + "\"")

        if (root.flags.isNotEmpty()) Log.warn("ResourcePacker", "Flags of root will not be processed.")

        val janitor = OperationJanitor(this.workingRootProvider)

        prepareOutputDirectory(janitor, this.to)

        for (task in this.tasks) {
            task.initializeForOperation(janitor)
        }

        for (setting in this.settings) {
            setting.activate()
        }

        for (task in this.tasks) {
            if (task.repeating) {
                var times = 0
                while (task.operate()) {
                    times += 1
                }
                while (root.applyTask(task)) {
                    logVirtualTreeAfter(task, root)
                    times += 1
                }
                Log.debug("ResourcePacker", "Task " + task.Name + " run " + times + " times")
            } else {
                val subMessage = if (task.operate()) "(did run in operate(void))" else "(did not run in operate(void))"
                if (root.applyTask(task)) {
                    logVirtualTreeAfter(task, root)
                    Log.debug("ResourcePacker", "Task " + task.Name + " finished and run " + subMessage)
                } else Log.debug("ResourcePacker", "Task " + task.Name + " finished but didn't run " + subMessage)
            }
        }

        for (setting in this.settings) {
            setting.reset()
        }

        root.copyYourself(this.to, useFolderAsRoot = true)

        janitor.dispose()
        Log.info("ResourcePacker", "Packing operation done (in " + "%.2f".format((System.currentTimeMillis() - startTime) / 1000f) + "s)")
    }
}