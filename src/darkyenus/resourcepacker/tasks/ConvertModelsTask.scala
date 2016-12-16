package darkyenus.resourcepacker.tasks

import java.io.{File, FileNotFoundException, FileOutputStream}
import java.net.URL

import com.esotericsoftware.minlog.Log
import com.google.common.base.Charsets
import com.google.common.io.{ByteStreams, Files}
import darkyenus.resourcepacker.{ResourceFile, Task}
import org.lwjgl.system.Platform

import scala.collection.convert.wrapAll._
import scala.io.Source
import scala.util.matching.Regex

/**
 * Converts obj and fbx files to g3db or different format.
 *
 * {{{
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
 * }}}
 *
 * @author Darkyen
 */
object ConvertModelsTask extends Task {

  private val MtlLibRegex = "mtllib ((?:\\w|/|-|\\.)+\\w\\.mtl)".r
  private val TextureRegex = "map_Kd ((?:\\w|/|-|\\.)+\\w\\.(?:png|jpg|jpeg))".r

  private def findDependentFiles(file: ResourceFile, regex: Regex): Iterable[ResourceFile] = {
    Files.readLines(file.file, Charsets.UTF_8).collect[Option[ResourceFile], Iterable[Option[ResourceFile]]] {
      case regex(dependencyFileName) =>
        val parts = dependencyFileName.split('/')
        var directory = file.parent
        var continue = true
        for (subdirectory <- parts.dropRight(1) if continue) {
          directory.getChildDirectory(subdirectory) match {
            case Some(newDirectory) =>
              directory = newDirectory
            case None =>
              Log.error(Name, "File references non-existing file. " + file + " " + dependencyFileName)
              continue = false
          }
        }
        if (continue) {
          directory.getChildFile(parts.last) match {
            case Some(dependencyFile) =>
              Some(dependencyFile)
            case None =>
              Log.error(Name, "File references non-existing file. " + file + " " + dependencyFileName)
              None
          }
        } else {
          None
        }
    }.flatten
  }

  private def removeObjFile(file: ResourceFile) {
    for (mtlFile <- findDependentFiles(file, MtlLibRegex)) {
      for (textureFile <- findDependentFiles(mtlFile, TextureRegex)) {
        textureFile.parent.removeChild(textureFile)
      }
      mtlFile.parent.removeChild(mtlFile)
    }
    file.removeFromParent()
  }

  private def copyObjAndDepsWithoutSpaces(file: ResourceFile) {
    val temp = newFolder()
    val objectFile = new File(temp, "object.obj")
    Files.copy(file.file, objectFile)
    file.file = objectFile

    for (mtlFile <- findDependentFiles(file, MtlLibRegex)) {
      val mtlFileFile = new File(temp, mtlFile.simpleName)
      Files.copy(mtlFile.file, mtlFileFile)
      mtlFile.file = mtlFileFile
      for (textureFile <- findDependentFiles(mtlFile, TextureRegex)) {
        val textureFileFile = new File(temp, textureFile.simpleName)
        Files.copy(textureFile.file, textureFileFile)
        textureFile.file = textureFileFile
      }
    }
  }

  private val OptionsRegex = "options ?((?:\\w| |-)+)".r

  private val ConversionOptionsRegex = "to (fbx|g3dj|g3db)".r

  override def operate(modelFile: ResourceFile): Boolean = {
    if (modelFile.extension != "obj" && modelFile.extension != "fbx") return false
    val isObj = modelFile.extension == "obj"

    val options = modelFile.flags.collectFirst {
      case OptionsRegex(opts) => opts.trim
    }.getOrElse("")

    val convertTo = modelFile.flags.collectFirst {
      case ConversionOptionsRegex(format) =>
        format
    }.getOrElse("g3db")

    if (isObj) copyObjAndDepsWithoutSpaces(modelFile)

    val inputFilePath = modelFile.file.getCanonicalPath

    val outputFilePostfix = "." + convertTo
    val outputFile = new File(newFolder(), modelFile.name + outputFilePostfix)
    val outputFilePath = outputFile.getCanonicalPath

    val postCommandOptions = s" -o ${convertTo.toUpperCase} $options $inputFilePath $outputFilePath"

    Log.info(Name, "Converting " + modelFile.extension + " file. " + modelFile + " " + postCommandOptions + " " + options + " " + modelFile.flags.addString(new StringBuilder, "|").toString())

    if (isObj) removeObjFile(modelFile)
    else modelFile.removeFromParent()

    ExecutablesDirectory

    Platform.get() match {
      case Platform.MACOSX =>
        executeCommand(postCommandOptions, linkfbxsdk = true)
      case Platform.WINDOWS =>
        executeCommand(postCommandOptions, linkfbxsdk = false)
      case Platform.LINUX =>
        executeCommand(postCommandOptions, linkfbxsdk = true)
      case unk =>
        Log.error(Name, "Unknown platform reported by LWJGL: " + unk)
    }
    modelFile.parent.addChild(outputFile)
    true
  }

  private def copyResourceToFolder(destination: File, resource: String) {
    Files.createParentDirs(destination)
    val in = classOf[Task].getClassLoader.getResourceAsStream(resource)
    val out = new FileOutputStream(destination)
    ByteStreams.copy(in, out)
    out.flush()
    out.close()
    in.close()
    destination.setExecutable(true, false)
  }

  private var executable: File = _

  private lazy val ExecutablesDirectory: File = {
    val result = newFolder()
    Platform.get() match {
      case Platform.MACOSX =>
        executable = new File(result, "fbx-conv-mac")
        copyResourceToFolder(executable, "fbxconv/osx/fbx-conv-mac")
        copyResourceToFolder(new File(result, "libfbxsdk.dylib"), "fbxconv/osx/libfbxsdk.dylib")
      case Platform.WINDOWS =>
        executable = new File(result, "fbx-conv-win32.exe")
        copyResourceToFolder(executable, "fbxconv/windows/fbx-conv-win32.exe")
      case Platform.LINUX =>
        executable = new File(result, "fbx-conv-lin64")
        copyResourceToFolder(executable, "fbxconv/linux/fbx-conv-lin64")
        copyResourceToFolder(new File(result, "libfbxsdk.so"), "fbxconv/linux/libfbxsdk.so")
      case _ =>
    }
    result
  }

import scala.sys.process.Process

  private def executeCommand(postCommand: String, linkfbxsdk: Boolean) {
    val command = executable.getCanonicalPath + postCommand
    Log.info(Name, "Executing external command. " + command)

    val processResult = if (linkfbxsdk) {
      Process(command, None, "LD_LIBRARY_PATH" -> executable.getParent).!
    } else {
      Process(command).!
    }

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
