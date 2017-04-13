package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import java.io.File

import com.esotericsoftware.minlog.Log
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.google.common.io.ByteStreams
import org.lwjgl.system.Platform
import java.io.FileOutputStream

/**
 * Converts obj and fbx files to g3db or different format.
 *
 * ```
 *   Add options:
 *   options -option1 -option2
 *
 *   Set output format:
 *   to <format>
 *
 *   Supported formats:
 *   fbx
 *   g3dj
 *   g3db <- default
 *
 * ```
 *
 * @author Darkyen
 */
object ConvertModelsTask : Task() {

    private val MtlLibRegex = Regex("mtllib ((?:\\w|/|-|\\.)+\\w\\.mtl)")
    private val TextureRegex = Regex("map_Kd ((?:\\w|/|-|\\.)+\\w\\.(?:png|jpg|jpeg))")

    private fun findDependentFiles(file: ResourceFile, regex: Regex): List<ResourceFile> {
        val result = ArrayList<ResourceFile>()
        Files.readLines(file.file, Charsets.UTF_8).matchAll(regex){(dependencyFileName) ->
            val parts = dependencyFileName.split('/')
            var directory = file.parent
            for (subdirectory in parts.dropLast(1)) {
                val dir = directory.getChildDirectory(subdirectory)
                if (dir != null) {
                    directory = dir
                } else {
                    Log.error(Name, "File references non-existing file. $file -> $dependencyFileName")
                    return@matchAll
                }
            }
            val dependentFile = directory.getChildFile(parts.last())
            if (dependentFile != null) {
                result.add(dependentFile)
            } else {
                Log.error(Name, "File references non-existing file. $file -> $dependencyFileName")
            }
        }

        return result
    }

    private fun removeObjFile(file: ResourceFile) {
        for (mtlFile in findDependentFiles(file, MtlLibRegex)) {
            for (textureFile in findDependentFiles(mtlFile, TextureRegex)) {
                textureFile.parent.removeChild(textureFile) //TODO Is this correct?
            }
            mtlFile.parent.removeChild(mtlFile)
        }
        file.removeFromParent()
    }

    private fun copyObjAndDepsWithoutSpaces(file: ResourceFile) {
        val temp = newFolder()
        val objectFile = File(temp, "object.obj")
        Files.copy(file.file, objectFile)
        file.file = objectFile

        for (mtlFile in findDependentFiles(file, MtlLibRegex)) {
            val mtlFileFile = File(temp, mtlFile.simpleName)
            Files.copy(mtlFile.file, mtlFileFile)
            mtlFile.file = mtlFileFile
            for (textureFile in findDependentFiles(mtlFile, TextureRegex)) {
                val textureFileFile = File(temp, textureFile.simpleName)
                Files.copy(textureFile.file, textureFileFile)
                textureFile.file = textureFileFile
            }
        }
    }

    private val OptionsRegex = Regex("options ?((?:\\w| |-)+)")

    private val ConversionOptionsRegex = Regex("to (fbx|g3dj|g3db)")

    override fun operate(file: ResourceFile): Boolean {
        if (file.extension != "obj" && file.extension != "fbx") return false
        val isObj = file.extension == "obj"

        val options = file.flags.matchFirst(OptionsRegex) { (opts) ->
            opts.trim()
        } ?: ""

        val convertTo = file.flags.matchFirst(ConversionOptionsRegex) { (format) ->
            format.toUpperCase()
        } ?: return false

        if (isObj) copyObjAndDepsWithoutSpaces(file)

        val inputFilePath = file.file.canonicalPath

        val outputFilePostfix = "." + convertTo
        val outputFile = File(newFolder(), file.name + outputFilePostfix)
        val outputFilePath = outputFile.canonicalPath

        val args = ArrayList<String>()
        args.add("-o")
        args.add(convertTo)
        args.add(options)
        args.add(inputFilePath)
        args.add(outputFilePath)

        Log.info(Name, "Converting " + file.extension + " file. " + file + " " + args + " " + options + " " + file.flags.joinToString("."))

        when(Platform.get()) {
            Platform.MACOSX ->
                executeCommand(args, linkFbxSdk = true)
            Platform.WINDOWS ->
                executeCommand(args, linkFbxSdk = false)
            Platform.LINUX ->
                executeCommand(args, linkFbxSdk = true)
            else -> {
                Log.error(Name, "Unknown platform reported by LWJGL: " + Platform.get())
                return false
            }

        }

        if (isObj) removeObjFile(file)
        else file.removeFromParent()

        file.parent.addChild(outputFile)
        return true
    }

    private fun copyResourceToFolder(destination: File, resource: String) {
        Files.createParentDirs(destination)
        val inp = ConvertModelsTask::class.java.classLoader.getResourceAsStream(resource)
        val out = FileOutputStream(destination)
        ByteStreams.copy(inp, out)
        out.flush()
        out.close()
        inp.close()
        destination.setExecutable(true, false)
    }

    private val fbxConvExecutable:File by lazy {
        val executableFolder = newFolder()
        val executable:File
        val platform = Platform.get()
        when(platform) {
            Platform.MACOSX -> {
                executable = File (executableFolder, "fbx-conv-mac")
                copyResourceToFolder(executable, "fbxconv/osx/fbx-conv-mac")
                copyResourceToFolder(File (executableFolder, "libfbxsdk.dylib"), "fbxconv/osx/libfbxsdk.dylib")
            }
            Platform.WINDOWS -> {
                executable = File(executableFolder, "fbx-conv-win32.exe")
                copyResourceToFolder(executable, "fbxconv/windows/fbx-conv-win32.exe")
            }
            Platform.LINUX -> {
                executable = File(executableFolder, "fbx-conv-lin64")
                copyResourceToFolder(executable, "fbxconv/linux/fbx-conv-lin64")
                copyResourceToFolder(File(executableFolder, "libfbxsdk.so"), "fbxconv/linux/libfbxsdk.so")
            }
            else -> {
                error("Failed to prepare fbx-conv executable, unknown platform: "+platform)
            }
        }
        return@lazy executable
    }

    private fun executeCommand(arguments: ArrayList<String>, linkFbxSdk: Boolean) {
        val command = fbxConvExecutable.canonicalPath
        Log.info(Name, "Executing external command. " + command)

        arguments.add(0, command)

        val processBuilder = ProcessBuilder()
                .command(arguments)

        if (linkFbxSdk) {
            processBuilder.environment().put("LD_LIBRARY_PATH", fbxConvExecutable.parentFile.canonicalPath)
        }

        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)

        val process = processBuilder.start()
        val processResult = process.waitFor()

        if (processResult == 0) {
            Log.info(Name, "External command terminated normally.")
        } else if (processResult == 127) { // Could not link fbx sdk
            Log.error(Name, "Missing FBX SDK or it is not linked properly.")
            if (Platform.get() == Platform.WINDOWS) {
                Log.info(Name, "Try downloading and installing manually. From: http://www.microsoft.com/en-us/download/confirmation.aspx?id=5555")
            }
        } else {
            Log.warn(Name, "External command terminated with unusual code. " + processResult)
        }
    }
}
