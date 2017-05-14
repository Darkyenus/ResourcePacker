package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.Resource
import com.darkyen.resourcepacker.Task

/**
 * Transitively applies flag to all files inside a directory.
 *
 * ```
 * .* <flag>. Applies <flag> to direct subdirectories
 * .** <flag>. Applies <flag> to ALL subdirectories (direct and their direct and ...)
 * .*N <flag>. Applies <flag> to N levels of subdirectories
 *
 * * is equal to *1,
 * ** is theoretically equal to *INFINITY
 * ```
 */
object TransitiveFlagTask : Task() {

    val Direct = Regex("\\* (.*)")
    val All = Regex("\\*\\* (.*)")
    val N = Regex("\\*(\\d+) (.*)")

    fun applyFlagToSubdirectories(directory: Resource.ResourceDirectory, flag:String, remainingLayers:Int) {
        for (file in directory.files) {
            file.flags.add(flag)
        }

        for (dir in directory.directories) {
            dir.flags.add(flag)
        }

        if (remainingLayers > 1) {
            for (dir in directory.directories) {
                applyFlagToSubdirectories(dir, flag, remainingLayers - 1)
            }
        }
    }

    override fun operate(directory: Resource.ResourceDirectory): Boolean {
        var operated = false

        for (flag in directory.flags) {
            val directMatch = Direct.matchEntire(flag)
            val allMatch = All.matchEntire(flag)
            val nMatch = N.matchEntire(flag)

            val layers:Int
            val appliedFlag:String

            if (directMatch != null) {
                layers = 1
                appliedFlag = directMatch.groupValues[1]
            } else if (allMatch != null) {
                layers = Int.MAX_VALUE
                appliedFlag = allMatch.groupValues[1]
            } else if (nMatch != null) {
                layers = nMatch.groupValues[1].toInt()
                if (layers == 0) continue
                appliedFlag = nMatch.groupValues[2]
            } else continue

            applyFlagToSubdirectories(directory, appliedFlag, layers)
            operated = true
        }

        return operated
    }
}