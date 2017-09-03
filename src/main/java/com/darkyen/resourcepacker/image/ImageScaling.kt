package com.darkyen.resourcepacker.image

import java.awt.RenderingHints

enum class ImageScaling(val scalingName:String, internal val multiStepDownscale:Boolean, internal val awtFlag:Any) {
    Nearest("nearest", false, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR),
    Bilinear("bilinear", true, RenderingHints.VALUE_INTERPOLATION_BILINEAR),
    Bicubic("bicubic", false, RenderingHints.VALUE_INTERPOLATION_BICUBIC),
}