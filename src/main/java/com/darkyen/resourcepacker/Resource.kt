package com.darkyen.resourcepacker

import com.badlogic.gdx.utils.SnapshotArray
import com.darkyen.resourcepacker.util.SnapshotArrayList
import com.esotericsoftware.minlog.Log
import java.io.File
import java.nio.file.Files
import com.badlogic.gdx.utils.Array as GdxArray

/**

 */
@Suppress("unused")
sealed class Resource {

    abstract var parent: ResourceDirectory

    abstract val name: String

    abstract val flags:List<String>

    /**
     * Runs given task on itself and children, recursively.
     * @return whether or not it succeeded at least once (on me or someone else)
     */
    abstract fun applyTask(task: Task): Boolean

    fun removeFromParent() {
        parent.removeChild(this)
    }

    fun copyFlags():ArrayList<String> = ArrayList<String>(flags.size+2).apply {addAll(flags)}

    inline fun copyFlagsExcept(remove:(String)->Boolean):ArrayList<String> = ArrayList<String>(flags.size+2).apply {
        for (flag in flags) {
            if (!remove(flag)) {
                add(flag)
            }
        }
    }

    class ResourceDirectory(var directory: File, parent: ResourceDirectory?, private val pathPrefix:String? = null) : Resource() {

        override var parent: ResourceDirectory = parent ?: this

        override val name: String
        override val flags: MutableList<String>

        init {
            val parsedName = parseName(directory.name, false)
            this.name = parsedName.first
            this.flags = parsedName.second
        }

        private val childDirectories = SnapshotArrayList(false, 16, ResourceDirectory::class.java)
        private val childFiles = SnapshotArrayList(false, 16, ResourceFile::class.java)

        private val removedChildDirectories = GdxArray<ResourceDirectory>(false, 16, ResourceDirectory::class.java)
        private val removedChildFiles = GdxArray<ResourceFile>(false, 16, ResourceFile::class.java)

        /**
         * List of all directories currently present in this virtual directory.
         * Do not iterate over this list if you want to call add/remove child on this directory while iterating!!!
         * For that, use [forEachDirectory] instead.
         */
        val directories: List<ResourceDirectory>
            get() = childDirectories

        /**
         * List of all files currently present in this virtual directory.
         * Do not iterate over this list if you want to call add/remove child on this directory while iterating!!!
         * For that, use [forEachFile] instead.
         */
        val files: List<ResourceFile>
            get() = childFiles

        fun hasChildren(): Boolean = childDirectories.isNotEmpty() || childFiles.isNotEmpty()

        fun removeChild(dir: ResourceDirectory) {
            if (childDirectories.removeValue(dir, true)) {
                removedChildDirectories.add(dir)
            } else {
                Log.warn("Removing directory which doesn't exist: $dir")
            }
        }

        fun removeChild(file: ResourceFile) {
            if (childFiles.removeValue(file, true)) {
                removedChildFiles.add(file)
            } else {
                Log.warn("Removing file which doesn't exist: $file")
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

        inline fun forEachDirectory(action:(ResourceDirectory) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            val directories = directories as SnapshotArray<ResourceDirectory>
            val array = directories.begin()
            val size = directories.size
            try {
                for (i in 0 until size) {
                    action(array[i])
                }
            } finally {
                directories.end()
            }
        }

        inline fun forEachDirectory(filter:(ResourceDirectory) -> Boolean, action:(ResourceDirectory) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            val directories = directories as SnapshotArray<ResourceDirectory>
            val array = directories.begin()
            val size = directories.size
            try {
                for (i in 0 until size) {
                    val item = array[i]
                    if (filter(item)) {
                        action(array[i])
                    }
                }
            } finally {
                directories.end()
            }
        }

        inline fun forEachFile(action:(ResourceFile) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            val files = files as SnapshotArray<ResourceFile>
            val array = files.begin()
            val size = files.size
            try {
                for (i in 0 until size) {
                    action(array[i])
                }
            } finally {
                files.end()
            }
        }

        inline fun forEachFile(filter:(ResourceFile) -> Boolean, action:(ResourceFile) -> Unit) {
            @Suppress("UNCHECKED_CAST")
            val files = files as SnapshotArray<ResourceFile>
            val array = files.begin()
            val size = files.size
            try {
                for (i in 0 until size) {
                    val item = array[i]
                    if (filter(item)) {
                        action(array[i])
                    }
                }
            } finally {
                files.end()
            }
        }

        inline fun forEach(action: (Resource) -> Unit) {
            forEachDirectory(action)
            forEachFile(action)
        }

        fun addChild(file: ResourceDirectory): ResourceDirectory {
            childDirectories.add(file)
            file.parent = this
            return file
        }

        fun addChild(file: ResourceFile): ResourceFile {
            childFiles.add(file)
            file.parent = this
            return file
        }

        fun addChild(res: Resource): Resource {
            return when (res) {
                is ResourceDirectory ->
                    addChild(res)
                is ResourceFile ->
                    addChild(res)
            }
        }

        fun addChild(javaFile: File, createStructure: Boolean = true): Resource? {
            if (!javaFile.name.startsWith('.') && (javaFile.isDirectory || javaFile.isFile)) {
                if (javaFile.isFile) {
                    val file = ResourceFile(javaFile, this)
                    childFiles.add(file)
                    return file
                } else {
                    val dir = ResourceDirectory(javaFile, this)
                    childDirectories.add(dir)
                    if (createStructure) {
                        dir.addResourceChildrenFromFilesystem()
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

        fun getChildDirectory(name: String): ResourceDirectory? {
            return childDirectories.find { it.name == name } ?: removedChildDirectories.find { it.name == name }
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
                    return childFiles.find { it.name == newName && it.extension == extension } ?: removedChildFiles.find { it.name == newName && it.extension == extension }
                }
            } else {
                return childFiles.find { it.name == name } ?: removedChildFiles.find { it.name == name }
            }
        }

        fun addResourceChildrenFromFilesystem() {
            for (file in directory.listFiles() ?: emptyArray()) {
                addChild(file, createStructure = true)
            }
        }

        internal fun stripWorkingDirectoryPath(path:String):String {
            var root = this
            while (root.parent != root) {
                root = root.parent
            }
            val prefix = root.pathPrefix ?: return path
            if (path.startsWith(prefix)) {
                return "#"+path.substring(prefix.length)
            } else {
                return path
            }
        }

        override fun toString(): String {
            val builder = StringBuilder()
            builder.append("Dir: ")
            builder.append(stripWorkingDirectoryPath(directory.canonicalPath))
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

            val filesSnapshot = childFiles.begin()
            val filesSize = (childFiles as GdxArray<ResourceFile>).size

            val directoriesSnapshot = childDirectories.begin()
            val directoriesSize = (childDirectories as GdxArray<ResourceDirectory>).size

            for (i in 0 until filesSize) {
                if (filesSnapshot[i].applyTask(task)) {
                    wasSuccessful = true
                }
            }

            for (i in 0 until directoriesSize) {
                if (directoriesSnapshot[i].applyTask(task)) {
                    wasSuccessful = true
                }
            }

            childFiles.end()
            childDirectories.end()

            return wasSuccessful
        }

        fun copyYourself(folder: File, useFolderAsRoot: Boolean = false, preferSymlinks:Boolean = false) {
            val myFolder = if (useFolderAsRoot) folder
            else {
                val result = File(folder, name)
                result.mkdirs()
                result
            }
            for (file in childFiles) {
                file.copyYourself(myFolder, preferSymlinks)
            }
            for (dir in childDirectories) {
                dir.copyYourself(myFolder, false, preferSymlinks)
            }
        }

        fun toPrettyString(sb: StringBuilder, level: Int) {
            fun appendLevel() {
                var i = 0
                while (i < level) {
                    sb.append("    ")
                    i += 1
                }
            }

            for (file in childFiles.sortedBy { it.name }) {
                appendLevel()
                sb.append(file.name).append('.').append(file.extension).append('\n')
            }

            for (directory in childDirectories.sortedBy { it.name }) {
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
            override val name: String,
            override val flags: MutableList<String>,
            val extension: String) : Resource() {

        var file: File = file
            get() {
                if (!field.exists() || !field.isFile) {
                    error("This should not happen - given file does not exist. (${field.canonicalPath})")
                }
                return field
            }

        private constructor(file: File, parent: ResourceDirectory, parseName: Triple<String, MutableList<String>, String>) : this(file, parent, parseName.first, parseName.second, parseName.third)

        constructor(file: File, parent: ResourceDirectory) : this(file, parent, parseName(file.name, true))

        /** Name without flags with extension */
        val simpleName: String = if (extension.isEmpty()) this.name else this.name + '.' + this.extension

        override fun toString(): String {
            val builder = StringBuilder()
            builder.append(parent.stripWorkingDirectoryPath(file.canonicalPath))
            builder.append(" (")
            builder.append(name)
            for (flag in flags) {
                builder.append('.').append(flag)
            }
            builder.append('.').append(extension)
            builder.append(")")
            return builder.toString()
        }

        fun copyYourself(folder: File, preferSymlinks:Boolean = false) {
            val createdFile = File(folder, simpleName)

            if (preferSymlinks) {
                // Determine if symlinking is possible for this file
                var parent = parent
                while (parent.parent != parent) {
                    parent = parent.parent
                }

                val myPath = file.canonicalPath
                val resourceDirPath = parent.directory.canonicalPath
                if (myPath.startsWith(resourceDirPath)) {
                    // This file is still inside the resource directory, symlinking is meaningful!
                    try {
                        Files.createSymbolicLink(createdFile.toPath(), file.canonicalFile.toPath())
                        return
                    } catch (ex:Exception) {
                        Log.warn("Failed to symlink $file to $createdFile, file will be copied", ex)
                    }
                }
            }

            Files.copy(file.toPath(), createdFile.toPath())
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
        fun parseName(fileName: String, withExtension: Boolean): Triple<String, MutableList<String>, String> {
            val name = StringBuilder()
            val flags = ArrayList<String>()
            val extension: String

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
    }
}

fun Resource.ResourceFile.isBitmapImage(): Boolean = extension == "png" || extension == "jpg" || extension == "jpeg" || extension == "gif" || extension == "bmp"
fun Resource.ResourceFile.isVectorImage(): Boolean = extension == "svg" || extension == "svgz"
fun Resource.ResourceFile.isImage(): Boolean = isBitmapImage() || isVectorImage()
fun Resource.ResourceFile.isFont(): Boolean = extension == "ttf" || extension == "otf"