package darkyenus.resourcepacker.tasks

import com.esotericsoftware.minlog.Log
import com.google.common.base.Charsets
import com.google.common.io.Files
import darkyenus.resourcepacker.{ResourceDirectory, ResourceFile, Task}

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
object CreateAppleStringsTask extends Task {

  val CreateStringsFlag = "AppleStrings".toLowerCase

  def escape(text:String):CharSequence = {
    val result = new StringBuilder

    for(c <- text){
      c match {
        case '\\' =>
          result.append("\\\\")
        case '"' =>
          result.append("\\\"")
        case '\n' =>
          result.append("\\n")
        case '\r' =>
          result.append("\\r")
        case normal =>
          result.append(normal)
      }
    }

    result
  }

  override def operate(directory: ResourceDirectory): Boolean = {
    Log.warn(Name, directory.toString)
    if(directory.flags.contains(CreateStringsFlag)){
      type FileName = String
      type FileContent = String

      val strings = new StringBuilder()

      for(stringFile <- directory.files){
        val content = Files.toString(stringFile.file, Charsets.UTF_8)
        strings.append('"').append(escape(stringFile.name)).append("\" = \"").append(escape(content)).append("\";\n")
      }

      directory.removeFromParent()

      val resultJavaFile = newBlankFile(directory.name, "strings")
      Files.write(strings, resultJavaFile, Charsets.UTF_16)

      directory.parent.addChild(new ResourceFile(resultJavaFile, directory.parent){
        override val name: String = directory.name
        override val flags: Array[FileContent] = directory.flags.filterNot(_ == CreateStringsFlag)
        override val extension: FileContent = "strings"
      })

      Log.info(Name, ".strings file from "+directory+" created")
      true
    }else false
  }
}
