package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.Resource
import com.darkyen.resourcepacker.Resource.ResourceDirectory
import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.esotericsoftware.minlog.Log
import com.google.common.base.Charsets
import com.google.common.io.Files

/**
 * Creates Apple `.strings` file from directory with `AppleStrings` flag.
 * Files in the directory are taken, their name is used as a strings key and their content (assumed to be UTF-8)
 * is used as strings value.
 *
 * Whole directory is then removed and its flags are carried to the created file.
 *
 * .strings reference:
 * https://developer.apple.com/library/prerelease/ios/documentation/Cocoa/Conceptual/LoadingResources/Strings/Strings.html
 */
object CreateAppleStringsTask : Task() {

    val CreateStringsFlag = "AppleStrings".toLowerCase()

    fun escape(text:String):CharSequence {
        val result = StringBuilder()
        for (c in text) {
            when (c) {
                '\\' -> result.append("\\\\")
                '"' -> result.append("\\\"")
                '\n' -> result.append("\\n")
                '\r' -> result.append("\\r")
                else -> result.append(c)
            }
        }
        return result
    }

    override fun operate(directory: ResourceDirectory): Boolean {
        if(directory.flags.contains(CreateStringsFlag)){
            val strings = StringBuilder()

            for(stringFile in directory.files){
                val content = Files.toString(stringFile.file, Charsets.UTF_8)
                strings.append('"').append(escape(stringFile.name)).append("\" = \"").append(escape(content)).append("\";\n")
            }

            directory.removeFromParent()

            val resultJavaFile = newBlankFile(directory.name, "strings")
            Files.write(strings, resultJavaFile, Charsets.UTF_16)

            val resourceFile = ResourceFile(
                    resultJavaFile, directory.parent,
                    directory.name,
                    directory.flags.filterNot { it == CreateStringsFlag },
                    "strings")
            directory.parent.addChild(resourceFile)

            Log.info(Name, ".strings file from $directory created")
            return true
        }

        return false
    }
}
