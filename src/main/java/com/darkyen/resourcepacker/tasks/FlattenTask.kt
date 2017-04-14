package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.Resource.ResourceDirectory
import com.darkyen.resourcepacker.Task
import com.esotericsoftware.minlog.Log

/**
 * Flattens directories marked with `flatten`.
 * @author Darkyen
 */
object FlattenTask : Task() {

    override val repeating: Boolean = true

    override fun operate(directory: ResourceDirectory): Boolean {
        if (directory.flags.contains("flatten")) {
            flatten(directory)
            Log.info(Name, "Directory flattened. ($directory)")
            return true
        }

        return false
    }

    fun flatten(directory: ResourceDirectory) {
        //Grandparent will no longer acknowledge this child and take all his children. Harsh.
        val grandparent = directory.parent
        grandparent.removeChild(directory)
        directory.forEach { child ->
            directory.removeChild(child)
            grandparent.addChild(child)
        }
    }
}
