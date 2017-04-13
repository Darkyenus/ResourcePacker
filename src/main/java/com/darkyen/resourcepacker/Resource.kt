package com.darkyen.resourcepacker

import java.io.File
import com.esotericsoftware.minlog.Log
import com.google.common.io.Files

/**

 */
@Suppress("unused")
sealed class Resource {

    abstract var parent: ResourceDirectory

    abstract val name: String

    /**
     * Runs given task on itself and children, recursively.
     * @return whether or not it succeeded at least once (on me or someone else)
     */
    abstract fun applyTask(task: Task): Boolean

    fun removeFromParent() {
        parent.removeChild(this)
    }

    class ResourceDirectory(var directory: File, parent: ResourceDirectory?) : Resource() {

        override var parent:ResourceDirectory = parent ?: this

        override val name:String
        val flags:List<String>

        init {
            val parsedName = parseName(directory.name, false)
            this.name = parsedName.first
            this.flags = parsedName.second
        }

        private val childrenDirectories = HashSet<ResourceDirectory>()
        private val childrenFiles = HashSet<ResourceFile>()

        private val removedFileChildren = ArrayList<ResourceFile>()
        private val removedDirChildren = ArrayList<ResourceDirectory>()

        val files:Set<ResourceFile>
            get() = childrenFiles

        val directories:Set<ResourceDirectory>
            get() = childrenDirectories

        fun hasChildren(): Boolean = childrenDirectories.isNotEmpty() || childrenFiles.isNotEmpty()

        fun removeChild(file: ResourceFile) {
            if (childrenFiles.remove(file)) {
                removedFileChildren.add(file)
            } else {
                System.err.println("WARN: Removing file which doesn't exist: $file")
            }
        }

        fun removeChild(dir: ResourceDirectory) {
            if (childrenDirectories.remove(dir)) {
                removedDirChildren.add(dir)
            } else {
                System.err.println("WARN: Removing directory which doesn't exist: $dir")
            }
        }

        fun removeChild(res: Resource) {
            when (res) {
                is ResourceDirectory ->
                        removeChild(res)
                is ResourceFile ->
                        removeChild(res)
            }
        }

        @Deprecated("Too slow")
        fun children():Set<Resource> {
            val result = HashSet<Resource>()
            result.addAll(childrenFiles)
            result.addAll(childrenDirectories)
            return result
        }

        inline fun forEachChild(action:(Resource)->Unit) {
            files.forEach(action)
            directories.forEach(action)
        }

        fun addChild(file: ResourceFile): ResourceFile {
            childrenFiles.add(file)
            file.parent = this
            return file
        }

        fun addChild(file: ResourceDirectory): ResourceDirectory {
            childrenDirectories.add(file)
            file.parent = this
            return file
        }

        fun addChild(res: Resource): Resource {
            return when (res) {
                is ResourceFile ->
                        addChild(res)
                is ResourceDirectory ->
                        addChild(res)
            }
        }

        fun addChild(javaFile: File, createStructure: Boolean = true): Resource? {
            if (!javaFile.name.startsWith('.') && (javaFile.isDirectory || javaFile.isFile)) {
                if (javaFile.isFile) {
                    val file = ResourceFile(javaFile, this)
                    childrenFiles.add(file)
                    return file
                } else {
                    val dir = ResourceDirectory(javaFile, this)
                    childrenDirectories.add(dir)
                    if (createStructure) {
                        dir.create()
                    }
                    return dir
                }
            } else {
                if (!javaFile.exists()) {
                    Log.warn("ResourceDirectory", "Child not added, because it doesn't exist. (\"" + javaFile.canonicalPath + "\")")
                }
                return null
            }
        }

        fun getChildFile(name: String): ResourceFile? {
            if (name.contains(".")) {
                val dotIndex = name.indexOf(".")
                if (dotIndex != name.lastIndexOf(".")) {
                    Log.error("ResourceDirectory", "There is no child file with two dots in name. There is an error. (\"$name\")")
                    return null
                } else {
                    val newName = name.substring(0, dotIndex)
                    val extension = name.substring(dotIndex + 1).toLowerCase()
                    return childrenFiles.find { it.name == newName && it.extension == extension } ?: removedFileChildren.find { it.name == newName && it.extension == extension }
                }
            } else {
                return childrenFiles.find { it.name == name } ?: removedFileChildren.find { it.name == name }
            }
        }

        fun getChildDirectory(name: String): ResourceDirectory? {
            return childrenDirectories.find { it.name == name } ?: removedDirChildren.find { it.name == name }
        }

        @Deprecated("TODO: Rename to something more meaningful")
        fun create() {
            for (file in directory.listFiles()) {
                addChild(file, createStructure = true)
            }
        }

        override fun toString(): String {
            val builder = StringBuilder()
            builder.append("Dir: ")
            builder.append(directory.canonicalPath) //TODO .replace(Task.TempFolderPath,"$TMP")
            builder.append(" (")
            builder.append(name)
            for (flag in flags) {
                builder.append('.').append(flag)
            }
            builder.append(")")
            return builder.toString()
        }

        override fun applyTask(task: Task): Boolean {
            var wasSuccessful = false
            if (task.operate(this)) {
                wasSuccessful = true
            }
            for (file in childrenFiles) {
                if (file.applyTask(task)) {
                    wasSuccessful = true
                }
            }
            for (dir in childrenDirectories) {
                if (dir.applyTask(task)) {
                    wasSuccessful = true
                }
            }
            return wasSuccessful
        }

        fun copyYourself(folder: File, useFolderAsRoot: Boolean = false) {
            val myFolder = if (useFolderAsRoot) folder
            else {
                val result = File(folder, name)
                result.mkdirs()
                result
            }
            for (file in childrenFiles) {
                file.copyYourself(myFolder)
            }
            for (dir in childrenDirectories) {
                dir.copyYourself(myFolder, false)
            }
        }

        fun toPrettyString(sb:StringBuilder, level:Int): Unit {
            fun appendLevel() {
                var i = 0
                while(i < level){
                    sb.append("    ")
                    i += 1
                }
            }

            for (file in childrenFiles.sortedBy { it.name }) {
                appendLevel()
                sb.append(file.name).append('.').append(file.extension).append('\n')
            }

            for (directory in childrenDirectories.sortedBy { it.name }) {
                appendLevel()
                sb.append(directory.name).append('/').append('\n')
                directory.toPrettyString(sb, level + 1)
            }
        }
    }

    /**
     * @property name Name without flags
     */
    class ResourceFile(
            file: File,
            override var parent: ResourceDirectory,
            override val name:String,
            val flags:List<String>,
            val extension:String) : Resource() {

        var file:File = file
            get() {
                if (!field.exists() || !field.isFile) {
                    error("This should not happen - given file does not exist. (${field.canonicalPath})")
                }
                return field
            }

        private constructor(file: File, parent: ResourceDirectory, parseName:Triple<String, List<String>, String>) : this(file, parent, parseName.first, parseName.second, parseName.third)

        constructor(file:File, parent: ResourceDirectory) : this(file, parent, parseName(file.name, false))

        /** Name without flags with extension */
        val simpleName:String = if (extension.isEmpty()) this.name else this.name + '.' + this.extension

        override fun toString(): String {
            val builder = StringBuilder()
            builder.append(file.canonicalPath) //TODO .replace(Task.TempFolderPath,"$TMP")
            builder.append(" (")
            builder.append(name)
            for (flag in flags) {
                builder.append('.').append(flag)
            }
            builder.append('.').append(extension)
            builder.append(")")
            return builder.toString()
        }

        fun copyYourself(folder: File) {
            Files.copy(file, File(folder, simpleName))
        }

        /**
         * Runs given task on itself and children, recursively.
         * @return whether or not it succeeded at least once (on me or someone else)
         */
        override fun applyTask(task: Task): Boolean {
            return task.operate(this)
        }
    }

    companion object {
        /**
         * Parse file/directory name into name, flags and extension, if requested and present
         */
        fun parseName(fileName: String, withExtension:Boolean):Triple<String, List<String>, String> {
            val name = StringBuilder()
            val flags = ArrayList<String>()
            val extension:String

            val nameParts = fileName.split('.')
            name.append(nameParts.first())
            for (namePart in nameParts.drop(1).dropLast(if (withExtension) 1 else 0)) {
                if (namePart.length > 1 && namePart.startsWith('"') && namePart.endsWith('"')) {
                    // Verbatim name part
                    name.append('.').append(namePart, 1, namePart.length - 2)
                } else {
                    // Flag
                    flags.add(namePart)
                }
            }

            extension = if (withExtension && nameParts.size >= 2) nameParts.last().toLowerCase() else ""
            return Triple(name.toString(), flags, extension)
        }

        fun ResourceFile.isImage():Boolean = extension == "png" || extension == "jpg" || extension == "jpeg" || extension == "gif"
        fun ResourceFile.isVectorImage():Boolean = extension == "svg"
        fun ResourceFile.isFont():Boolean = extension == "ttf" || extension == "otf"
    }
}
