package darkyenus.resourcepacker.tasks

import com.esotericsoftware.minlog.Log
import darkyenus.resourcepacker.{ResourceFile, Task}

/**
 * Automatically creates iOS icons from svg vector image.
 * Works by generating virtual files to be processed by other tasks.
 * Other flags than iOSIcon are transferred to virtual files.
 * When files are already present (by name), they are not created,
 * so specialized icons can be created.
 *
 * Flags:
 * {{{
 *   iOSIcon - Marks the file for conversion, for universal apps
 *   iPhoneIcon - Marks the file for conversion, for iPhone apps only
 *   iPadIcon - Marks the file for conversion, for iPad apps only
 * }}}
 *
 * Append `Small` to any flag to create small icons as well (used for spotlight & settings)
 * Append `Artwork` to any flag to create big artwork icon dimensions as well.
 * NOTE: Artwork does not currently work properly, because extension (png) is left there.
 *
 * Sizes are from Apple Developer Library:
 * https://developer.apple.com/library/ios/qa/qa1686/_index.html
 */
object CreateIOSIconTask extends Task {

  /**
   * Int - size, icons are always square
   * String - file name, without extension
   * Boolean - strip png extension ?
   */
  type IconType = (Int, String, Boolean)

  type IconClass = ((Iterable[String] => Boolean), Iterable[IconType])

  val UniversalFlag = "iOSIcon".toLowerCase
  val iPhoneFlag = "iPhoneIcon".toLowerCase
  val iPadFlag = "iPadIcon".toLowerCase

  val SmallFlagAppendage = "Small".toLowerCase
  val ArtworkFlagAppendage = "Artwork".toLowerCase

  val ArtworkSizes:Array[IconType] = Array(
    (512, "iTunesArtwork", true),
    (1024, "iTunesArtwork@2x", true)
  )

  val iPhoneSizes:Array[IconType] = Array(
    (120, "Icon-60@2x", false),
    (180, "Icon-60@3x", false),
    (76, "Icon-76", false),
    (152, "Icon-76@2x", false),
    //6.1 and earlier
    (57, "Icon", false),
    (114, "Icon@2x", false),
    (72, "Icon-72", false),
    (144, "Icon-72@2x", false)
  )

  val iPhoneSmallSizes:Array[IconType] = Array(
    (40, "Icon-Small-40", false),
    (80, "Icon-Small-40@2x", false),
    (120, "Icon-Small-40@3x", false),
    (29, "Icon-Small", false),
    (58, "Icon-Small@2x", false),
    (87, "Icon-Small@3x", false),
    //6.1 and earlier
    (50, "Icon-Small-50", false),
    (100, "Icon-Small-50@2x", false)
  )

  val iPadSizes:Array[IconType] = Array(
    (76, "Icon-76", false),
    (152, "Icon-76@2x", false),
    //6.1 and earlier
    (72, "Icon-72", false),
    (144, "Icon-72@2x", false)
  )

  val iPadSmallSizes:Array[IconType] = Array(
    (40, "Icon-Small-40", false),
    (80, "Icon-Small-40@2x", false),
    (29, "Icon-Small", false),
    (58, "Icon-Small@2x", false),
    //6.1 and earlier
    (50, "Icon-Small-50", false),
    (100, "Icon-Small-50@2x", false)
  )

  val UniversalSizes:Array[IconType] = Array(
    (120, "Icon-60@2x", false),
    (180, "Icon-60@3x", false),
    (76, "Icon-76", false),
    (152, "Icon-76@2x", false),
    //6.1 and earlier
    (57, "Icon", false),
    (114, "Icon@2x", false),
    (72, "Icon-72", false),
    (144, "Icon-72@2x", false)
  )

  val UniversalSmallSizes:Array[IconType] = Array(
    (40, "Icon-Small-40", false),
    (80, "Icon-Small-40@2x", false),
    (120, "Icon-Small-40@3x", false),
    (29, "Icon-Small", false),
    (58, "Icon-Small@2x", false),
    (87, "Icon-Small@3x", false), //Incorrect name on reference page?
    //6.1 and earlier
    (50, "Icon-Small-50", false),
    (100, "Icon-Small-50@2x", false)
  )

  def collectIconTypes(flag:String, base:Array[IconType], small:Array[IconType], artwork:Array[IconType]):Iterable[IconType] = {
    var result:Iterable[IconType] = base
    if(flag.contains(SmallFlagAppendage)){
      result = result ++ small
    }
    if(flag.contains(ArtworkFlagAppendage)){
      result = result ++ artwork
    }
    result
  }

  override def operate(file: ResourceFile): Boolean = {
    file.flags.collectFirst {
      case universal if universal.startsWith(UniversalFlag) =>
        collectIconTypes(universal, UniversalSizes, UniversalSmallSizes, ArtworkSizes)
      case iPhone if iPhone.startsWith(iPhoneFlag) =>
        collectIconTypes(iPhone, iPhoneSizes, iPhoneSmallSizes, ArtworkSizes)
      case iPad if iPad.startsWith(iPadFlag) =>
        collectIconTypes(iPad, iPadSizes, iPadSmallSizes, ArtworkSizes)
    } match {
      case Some(sizes) =>
        if(file.extension != RasterizeTask.SVGExtension){
          Log.warn(Name, "File is marked for iOS icon creation, but isn't a supported format (svg): "+file)
          return false
        }

        val transitiveFlags = file.flags.filterNot(f => f.startsWith(UniversalFlag) || f.startsWith(iPhoneFlag) || f.startsWith(iPadFlag))

        file.removeFromParent()

        for((size,filename, stripExtension) <- sizes){
          if (!file.parent.children.exists(_.name == filename)){
            val singleIconFile = new ResourceFile(file.file, file.parent) {
              override val name: String = filename
              override val extension: String = RasterizeTask.SVGExtension
              override val flags: Array[String] = (size+"x"+size) +: "rasterize" +: transitiveFlags
            }

            Log.debug(Name, "Icon file "+singleIconFile+" created")

            file.parent.addChild(singleIconFile)
          }else{
            Log.debug(Name, "Not creating icon file "+filename+", already exists.")
          }
        }

        true
      case None =>
        false
    }
  }
}
