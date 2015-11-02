package darkyenus.resourcepacker

import java.io.File

import com.esotericsoftware.minlog.Log
import com.google.common.io.Files

import scala.collection.mutable.ArrayBuffer

sealed trait Resource {

  def name: String

  /**
   * Runs given task on itself and children, recursively.
   * @return whether or not it succeeded at least once (on me or someone else)
   */
  def applyTask(task: Task): Boolean

  var parent: ResourceDirectory

  def removeFromParent() {
    parent.removeChild(this)
  }
}

class ResourceDirectory(var directory: File, var parent: ResourceDirectory) extends Resource {

  private val nameParts = directory.getName.split('.')

  def isVerbatimFlag(flag:String):Boolean = {
    flag.length >= 2 && flag.startsWith("\"") && flag.endsWith("\"")
  }

  val name = {
    (nameParts.head +: nameParts.tail.collect{
      case flag if isVerbatimFlag(flag) =>
        flag.substring(1, flag.length-1)
    }).mkString(".")
  }

  val flags = nameParts.tail.filterNot(isVerbatimFlag).map(_.toLowerCase)

  private var childrenDirectories = Set[ResourceDirectory]()
  private var childrenFiles = Set[ResourceFile]()

  private val removedFileChildren = new ArrayBuffer[ResourceFile]()
  private val removedDirChildren = new ArrayBuffer[ResourceDirectory]()

  def files: Iterable[ResourceFile] = childrenFiles

  def directories: Iterable[ResourceDirectory] = childrenDirectories

  def hasChildren: Boolean = !(childrenDirectories.isEmpty && childrenFiles.isEmpty)

  def removeChild(file: ResourceFile) {
    childrenFiles -= file
    removedFileChildren += file
  }

  def removeChild(dir: ResourceDirectory) {
    childrenDirectories -= dir
    removedDirChildren += dir
  }

  def removeChild(res: Resource) {
    res match {
      case file: ResourceFile =>
        removeChild(file)
      case dir: ResourceDirectory =>
        removeChild(dir)
    }
  }

  def children: Iterable[Resource] = childrenFiles ++ childrenDirectories

  def addChild(file: ResourceFile): ResourceFile = {
    childrenFiles += file
    file.parent = this
    file
  }

  def addChild(file: ResourceDirectory): ResourceDirectory = {
    childrenDirectories += file
    file.parent = this
    file
  }

  def addChild(res: Resource): Resource = {
    res match {
      case file: ResourceFile =>
        addChild(file)
      case dir: ResourceDirectory =>
        addChild(dir)
    }
  }

  def addChild(javaFile: File, createStructure: Boolean = true): Resource = {
    if (!javaFile.getName.startsWith(".") && (javaFile.isDirectory || javaFile.isFile)) {
      if (javaFile.isFile) {
        val file = new ResourceFile(javaFile, this)
        childrenFiles += file
        file
      } else {
        val dir = new ResourceDirectory(javaFile, this)
        childrenDirectories += dir
        if (createStructure) {
          dir.create()
        }
        dir
      }
    } else {
      if (!javaFile.exists()) {
        Log.warn("ResourceDirectory", "Child not added, because it doesn't exist. (\"" + javaFile.getCanonicalPath + "\")")
      }
      null
    }
  }

  def getChildFile(name: String): Option[ResourceFile] = {
    if (name.contains(".")) {
      val dotIndex = name.indexOf(".")
      if (dotIndex != name.lastIndexOf(".")) {
        Log.error("ResourceDirectory", "There is no child file with two dots in name. There is an error. (\"" + name + "\")")
        None
      } else {
        val newName = name.substring(0, dotIndex)
        val extension = name.substring(dotIndex + 1).toLowerCase
        childrenFiles.find(f => f.name == newName && f.extension == extension).orElse(removedFileChildren.find(f => f.name == newName && f.extension == extension))
      }
    } else {
      childrenFiles.find(_.name == name).orElse(removedFileChildren.find(_.name == name))
    }
  }

  def getChildDirectory(name: String): Option[ResourceDirectory] = {
    childrenDirectories.find(_.name == name).orElse(removedDirChildren.find(_.name == name))
  }

  def create() {
    for (file <- directory.listFiles()) {
      addChild(file, createStructure = true)
    }
  }

  override def toString: String = {
    val builder = new StringBuilder
    builder.append("Dir: ")
    builder.append(directory.getCanonicalPath) //TODO .replace(Task.TempFolderPath,"$TMP")
    builder.append(" (")
    builder.append(name)
    if (flags.nonEmpty) {
      builder.append('.')
      flags.addString(builder, ".")
    }
    builder.append(")")
    builder.toString()
  }

  override def applyTask(task: Task): Boolean = {
    var wasSuccessful = false
    if (task.operate(this)) {
      wasSuccessful = true
    }
    childrenFiles.foreach(child => {
      if (child.applyTask(task)) {
        wasSuccessful = true
      }
    })
    childrenDirectories.foreach(dir => {
      if (dir.applyTask(task)) {
        wasSuccessful = true
      }
    })
    wasSuccessful
  }

  def copyYourself(folder: File, root: Boolean = false) {
    val myFolder = if (root) folder
    else {
      val result = new File(folder, name)
      result.mkdirs()
      result
    }
    childrenFiles.foreach(_.copyYourself(myFolder))
    childrenDirectories.foreach(_.copyYourself(myFolder))
  }

  //noinspection AccessorLikeMethodIsUnit
  def toPrettyString(sb:StringBuilder, level:Int): Unit ={
    def appendLevel():StringBuilder = {
      var i = 0
      while(i < level){
        sb.append("    ")
        i += 1
      }
      sb
    }

    for(file <- childrenFiles.toSeq.sortBy(_.name)){
      appendLevel().append(file.name).append('.').append(file.extension).append('\n')
    }
    for(folder <- childrenDirectories.toSeq.sortBy(_.name)){
      appendLevel().append(folder.name).append('/').append('\n')
      folder.toPrettyString(sb,level+1)
    }
  }
}

class ResourceFile(private var _file: File, var parent: ResourceDirectory) extends Resource {
  private val nameParts = _file.getName.split('.')

  def isVerbatimFlag(flag:String):Boolean = {
    flag.length >= 2 && flag.startsWith("\"") && flag.endsWith("\"")
  }

  val name = {
    (nameParts.head +: nameParts.tail.collect{
      case flag if isVerbatimFlag(flag) =>
        flag.substring(1, flag.length-1)
    }).mkString(".")
  }

  val flags = nameParts.tail.filterNot(isVerbatimFlag).dropRight(1).map(_.toLowerCase)

  val extension = {
    val flagParts = nameParts.tail.filterNot(isVerbatimFlag)
    if(flagParts.isEmpty) "" else flagParts.last.toLowerCase
  }

  lazy val isImage: Boolean = {
    extension == "png" || extension == "jpg" || extension == "jpeg" || extension == "gif"
  }

  lazy val isFont: Boolean = {
    extension == "ttf" || extension == "otf"
  }

  lazy val simpleName: String = if (extension.isEmpty) name else name + '.' + extension

  override def toString: String = {
    val builder = new StringBuilder
    builder.append(_file.getCanonicalPath) //TODO .replace(Task.TempFolderPath,"$TMP")
    builder.append(" (")
    builder.append(name).append('.')
    if (flags.nonEmpty) {
      flags.addString(builder, ".")
      builder.append('.')
    }
    builder.append(extension)
    builder.append(")")
    builder.toString()
  }

  def file: File = _file

  def file_=(f: File) {
    if (!f.exists() || !f.isFile) {
      sys.error(s"This should not happen - given file does not exist. (${f.getCanonicalPath})")
    }
    _file = f
  }

  def copyYourself(folder: File) {
    Files.copy(_file, new File(folder, simpleName))
  }

  /**
   * Runs given task on itself and children, recursively.
   * @return whether or not it succeeded at least once (on me or someone else)
   */
  override def applyTask(task: Task): Boolean = {
    task.operate(this)
  }
}
