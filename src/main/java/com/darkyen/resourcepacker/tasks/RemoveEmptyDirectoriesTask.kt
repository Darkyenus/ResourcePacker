package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.Resource
import com.darkyen.resourcepacker.Task
import com.esotericsoftware.minlog.Log

/**
 * Removes all empty directories that don't have `retain` flag.
 * @author Darkyen
 */
object RemoveEmptyDirectoriesTask : Task() {

    override fun operate(directory: Resource.ResourceDirectory): Boolean {
        if (!directory.hasChildren() && !directory.flags.contains("retain")) {
            Log.info(Name, "Empty directory removed. " + directory)
            directory.parent.removeChild(directory)
            return true
        }

        return false
    }
}
