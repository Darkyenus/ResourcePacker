package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.Resource.ResourceDirectory
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.esotericsoftware.minlog.Log

/**
 * Removes all files and directories marked with `.ignore.`
 * @author Darkyen
 */
object IgnoreTask : Task() {

    override fun operate(file: ResourceFile): Boolean {
        if (file.flags.contains("ignore")) {
            file.removeFromParent()
            Log.info(Name, "File ignored. ($file)")
            return true
        }

        return false
    }

    override fun operate(directory: ResourceDirectory): Boolean {
        if (directory.flags.contains("ignore")) {
            directory.parent.removeChild(directory)
            Log.info(Name, "Directory ignored. ($directory)")
            return true
        }

        return false
    }
}
