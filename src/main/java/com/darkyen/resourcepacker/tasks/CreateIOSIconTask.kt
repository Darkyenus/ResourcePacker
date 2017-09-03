package com.darkyen.resourcepacker.tasks

import com.darkyen.resourcepacker.Resource.ResourceFile
import com.darkyen.resourcepacker.Task
import com.darkyen.resourcepacker.image.createImage
import com.darkyen.resourcepacker.image.saveToFile
import com.esotericsoftware.minlog.Log

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
 *
 * Sizes are from Apple Developer Library:
 * https://developer.apple.com/library/ios/qa/qa1686/_index.html
 */
object CreateIOSIconTask : Task() {

    /**
     * @param size icons are always square
     * @param name file name, without extension
     * @param stripExtension strip png extension ?
     */
    data class IconType(val size: Int, val name: String, val stripExtension: Boolean = false)

    val UniversalFlag: String = "iOSIcon".toLowerCase()
    val iPhoneFlag: String = "iPhoneIcon".toLowerCase()
    val iPadFlag: String = "iPadIcon".toLowerCase()

    val SmallFlagAppendage: String = "Small".toLowerCase()
    val ArtworkFlagAppendage: String = "Artwork".toLowerCase()

    val ArtworkSizes: Array<IconType> = arrayOf(
            IconType(512, "iTunesArtwork"),
            IconType(1024, "iTunesArtwork@2x")
    )

    val iPhoneSizes: Array<IconType> = arrayOf(
            IconType(120, "Icon-60@2x"),
            IconType(180, "Icon-60@3x"),
            IconType(76, "Icon-76"),
            IconType(152, "Icon-76@2x"),
            //6.1 and earlier
            IconType(57, "Icon"),
            IconType(114, "Icon@2x"),
            IconType(72, "Icon-72"),
            IconType(144, "Icon-72@2x")
    )

    val iPhoneSmallSizes: Array<IconType> = arrayOf(
            IconType(40, "Icon-Small-40"),
            IconType(80, "Icon-Small-40@2x"),
            IconType(120, "Icon-Small-40@3x"),
            IconType(29, "Icon-Small"),
            IconType(58, "Icon-Small@2x"),
            IconType(87, "Icon-Small@3x"),
            //6.1 and earlier
            IconType(50, "Icon-Small-50"),
            IconType(100, "Icon-Small-50@2x")
    )

    val iPadSizes: Array<IconType> = arrayOf(
            IconType(76, "Icon-76"),
            IconType(152, "Icon-76@2x"),
            //6.1 and earlier
            IconType(72, "Icon-72"),
            IconType(144, "Icon-72@2x")
    )

    val iPadSmallSizes: Array<IconType> = arrayOf(
            IconType(40, "Icon-Small-40"),
            IconType(80, "Icon-Small-40@2x"),
            IconType(29, "Icon-Small"),
            IconType(58, "Icon-Small@2x"),
            //6.1 and earlier
            IconType(50, "Icon-Small-50"),
            IconType(100, "Icon-Small-50@2x")
    )

    val UniversalSizes: Array<IconType> = arrayOf(
            IconType(120, "Icon-60@2x"),
            IconType(180, "Icon-60@3x"),
            IconType(76, "Icon-76"),
            IconType(152, "Icon-76@2x"),
            //6.1 and earlier
            IconType(57, "Icon"),
            IconType(114, "Icon@2x"),
            IconType(72, "Icon-72"),
            IconType(144, "Icon-72@2x")
    )

    val UniversalSmallSizes: Array<IconType> = arrayOf(
            IconType(40, "Icon-Small-40"),
            IconType(80, "Icon-Small-40@2x"),
            IconType(120, "Icon-Small-40@3x"),
            IconType(29, "Icon-Small"),
            IconType(58, "Icon-Small@2x"),
            IconType(87, "Icon-Small@3x"), //Incorrect name on reference page?
            //6.1 and earlier
            IconType(50, "Icon-Small-50"),
            IconType(100, "Icon-Small-50@2x")
    )

    fun collectIconTypes(flag: String, base: Array<IconType>, small: Array<IconType>, artwork: Array<IconType>): List<IconType> {
        val result = arrayListOf(*base)
        if (flag.contains(SmallFlagAppendage)) {
            result.addAll(small)
        }
        if (flag.contains(ArtworkFlagAppendage)) {
            result.addAll(artwork)
        }
        return result
    }

    override fun operate(file: ResourceFile): Boolean {
        for (flag in file.flags) {
            val iconTypes =
                    if (flag.startsWith(UniversalFlag)) {
                        collectIconTypes(flag, UniversalSizes, UniversalSmallSizes, ArtworkSizes)
                    } else if (flag.startsWith(iPhoneFlag)) {
                        collectIconTypes(flag, iPhoneSizes, iPhoneSmallSizes, ArtworkSizes)
                    } else if (flag.startsWith(iPadFlag)) {
                        collectIconTypes(flag, iPadSizes, iPadSmallSizes, ArtworkSizes)
                    } else continue

            val image = file.createImage()
            if (image == null) {
                Log.warn(Name, "File is marked for iOS icon creation, but isn't a supported image format: " + file)
                return false
            }

            val transitiveFlags = file.copyFlagsExcept {
                it.startsWith(UniversalFlag) || it.startsWith(iPhoneFlag) || it.startsWith(iPadFlag)
            }

            file.removeFromParent()

            for ((size, filename, stripExtension) in iconTypes) {
                if (file.parent.files.find { it.name == filename } == null) {
                    val resizedFile = newBlankFile(filename, "png")
                    image.image(width = size, height = size).saveToFile(resizedFile, "png")

                    val singleIconFile = ResourceFile(resizedFile, file.parent, filename, transitiveFlags, if (stripExtension) "" else "png")
                    file.parent.addChild(singleIconFile)

                    Log.debug(Name, "Icon file $singleIconFile created")
                } else {
                    Log.debug(Name, "Not creating icon file $filename, already exists.")
                }
            }

            return true
        }

        return false
    }
}
