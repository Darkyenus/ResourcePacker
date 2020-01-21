package com.darkyen.resourcepacker

import com.esotericsoftware.minlog.Log
import java.io.File
import java.nio.file.Files

/**
 * Class that keeps all working files in check, creates new ones and throws old ones away.
 *
 * @author Darkyen
 */
class OperationJanitor(private val workingRootProvider: WorkingRootProvider) {

    val workingRoot = workingRootProvider.getTemporaryRoot(this)

    fun createTempFile(taskName: String, fileName: String, file: Resource.ResourceFile, extension: String?): File {
        val sb = StringBuilder()
        var result: File
        do {
            sb.append(fileName).append('.')
            sb.append(taskName).append("-f-")
            fillWithRandomText(sb)
            sb.append('.')
            for (flag in file.flags) {
                sb.append('.').append(flag)
            }
            sb.append('.').append(extension ?: file.extension)
            result = File(workingRoot, sb.toString())
            sb.setLength(0)
            if (Log.DEBUG) Log.debug("OperationJanitor", "Trying to create file in \"" + workingRoot.absolutePath + "\" called \"" + sb + "\".")
        } while (result.exists())
        return result
    }

    fun createTempFile(taskName: String, fileName: String, extension: String?): File {
        val sb = StringBuilder()
        var result: File
        do {
            sb.append(fileName).append('.')
            sb.append(taskName).append("-f-")
            fillWithRandomText(sb)
            sb.append('.').append(extension ?: "")
            if (extension != null) {
                sb.append(extension)
            }
            result = File(workingRoot, sb.toString())
            if (Log.DEBUG) Log.debug("OperationJanitor", "Trying to create file from scratch in \"" + workingRoot.absolutePath + "\" called \"" + sb + "\".")
            sb.setLength(0)
        } while (result.exists())
        return result
    }

    fun createTempDirectory(taskName: String): File {
        val sb = StringBuilder()
        var result: File
        do {
            sb.append(taskName).append("-d-")
            fillWithRandomText(sb)
            result = File(workingRoot, sb.toString())
            sb.setLength(0)
        } while (result.exists())
        result.mkdirs()
        return result
    }

    private fun fillWithRandomText(b: StringBuilder, amount: Int = 6) {
        for (i in 1..amount) {
            if (i and 1 == 1) {
                b.append(Consonants[Random.nextInt(Consonants.size)])
            } else {
                b.append(Vowels[Random.nextInt(Vowels.size)])
            }
        }
    }

    fun clearFolder(dir: File, deleteDir: Boolean = false) {
        if (dir.isDirectory) {
            for (file in dir.listFiles() ?: emptyArray()) {
                if (file.isDirectory) {
                    clearFolder(file, deleteDir = true)
                } else {
                    if (!file.delete()) {
                        Log.warn("OperationJanitor", "File ${file.absolutePath} not deleted.")
                    }
                }
            }
            if (deleteDir && !dir.delete()) {
                Log.warn("OperationJanitor", "Directory ${dir.absolutePath} not deleted.")
            }
        } else if (dir.isFile) {
            Log.warn("OperationJanitor", "Directory ${dir.absolutePath} is actually a file.")
        }
    }

    fun dispose() {
        if (workingRootProvider.shouldDeleteRoot) {
            clearFolder(workingRoot, deleteDir = true)
        }
    }

    companion object {
        private val Vowels: CharArray = charArrayOf('a', 'e', 'i', 'o', 'u', 'y')
        private val Consonants: CharArray = charArrayOf('b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'z')
        private val Random = java.util.Random()
    }
}

interface WorkingRootProvider {
    fun getTemporaryRoot(operationJanitor: OperationJanitor): File

    val shouldDeleteRoot: Boolean
}

object TemporaryWorkingRootProvider : WorkingRootProvider {
    override fun getTemporaryRoot(operationJanitor: OperationJanitor): File = Files.createTempDirectory("resource-packer").toFile()

    override val shouldDeleteRoot: Boolean = true
}

class LocalWorkingRootProvider(val workingRoot: File) : WorkingRootProvider {

    constructor(workingRoot: String) : this(File(workingRoot))

    override fun getTemporaryRoot(operationJanitor: OperationJanitor): File {
        workingRoot.mkdirs()
        operationJanitor.clearFolder(workingRoot)
        return workingRoot
    }

    override val shouldDeleteRoot: Boolean = false
}