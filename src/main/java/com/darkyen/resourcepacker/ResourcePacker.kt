@file:JvmName("ResourcePacker")
@file:JvmMultifileClass

package com.darkyen.resourcepacker

import com.badlogic.gdx.utils.GdxNativesLoader
import com.darkyen.resourcepacker.tasks.DefaultTasks
import com.esotericsoftware.minlog.Log
import java.io.File

/*
 * Entry-point for the Resource Packer
 */

val PreferSymlinks = SettingKey("PreferSymlinks", false,
        "Instructs packing operation to symlink files instead of copying them. " +
                "This is possible only on systems that support symlinks and only for files, " +
                "that still after packing point into the original resources directory.")

/**
 * Launches all [Task]s, one after another.
 */
@JvmOverloads
fun packResources(from: File, to: File,
                  settings: List<Setting<*>> = emptyList(),
                  tasks: List<Task> = DefaultTasks,
                  workingRootProvider: WorkingRootProvider = TemporaryWorkingRootProvider) {
    GdxNativesLoader.load()

    /**
     * Does the actual work. Should be called only by launcher that has created a necessary context for tasks.
     */
    val startTime = System.currentTimeMillis()
    val root = createTree(from) ?: return
    Log.info("ResourcePacker", "Starting packing operation from \"${from.canonicalPath}\" to \"${to.canonicalPath}\"")

    if (root.flags.isNotEmpty()) Log.warn("ResourcePacker", "Flags of root will not be processed.")

    val janitor = OperationJanitor(workingRootProvider)

    prepareOutputDirectory(janitor, to)

    for (task in tasks) {
        task.initializeForOperation(janitor)
    }

    for (setting in settings) {
        setting.activate()
    }

    for (task in tasks) {
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

    val preferSymlinks = PreferSymlinks.get()

    for (setting in settings) {
        setting.reset()
    }

    root.copyYourself(to, useFolderAsRoot = true, preferSymlinks = preferSymlinks)

    janitor.dispose()
    Log.info("ResourcePacker", "Packing operation done (in " + "%.2f".format((System.currentTimeMillis() - startTime) / 1000f) + "s)")
}

private fun createTree(root: File): Resource.ResourceDirectory? {
    if (!root.isDirectory) {
        Log.error("ResourcePacker", "${root.canonicalPath} is not a directory.")
        return null
    }
    val result = Resource.ResourceDirectory(root, null, root.canonicalPath)
    result.addResourceChildrenFromFilesystem()
    return result
}

private fun prepareOutputDirectory(janitor: OperationJanitor, to: File) {
    janitor.clearFolder(to)
    if (!to.exists() && !to.mkdirs()) {
        Log.warn("ResourcePacker", "Output directory at \"${to.canonicalPath}\" could not be created. Assuming it's fine.")
    }
}

private fun logVirtualTreeAfter(after: Task, tree: Resource.ResourceDirectory) {
    if (Log.DEBUG) {
        Log.debug("PackingOperation", "After running ${after.Name}, virtual filesystem looks like this:")
        val sb = StringBuilder()
        sb.append('\n') //Logger already prints something on the line, so this makes it even
        tree.toPrettyString(sb, 1)
        Log.debug(sb.toString())
    }
}
