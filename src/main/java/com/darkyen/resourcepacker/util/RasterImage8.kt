package com.darkyen.resourcepacker.util

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.darkyen.resourcepacker.image.ImageScaling
import com.esotericsoftware.minlog.Log
import org.lwjgl.stb.STBImage
import org.lwjgl.stb.STBImageResize
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

typealias RGBA8 = Int

fun rgba8(r:Int, g:Int, b:Int, a:Int):RGBA8 {
	return rgba8(MathUtils.clamp(r, 0, 0xFF).toByte(),
			MathUtils.clamp(g, 0, 0xFF).toByte(),
			MathUtils.clamp(b, 0, 0xFF).toByte(),
			MathUtils.clamp(a, 0, 0xFF).toByte())
}

fun rgba8(r:Byte, g:Byte, b:Byte, a:Byte):RGBA8 {
	return ((r.toInt() and 0xFF) shl 24) or
			((g.toInt() and 0xFF) shl 16) or
			((b.toInt() and 0xFF) shl 8) or
			(a.toInt() and 0xFF)
}

/** Rounded division by 255 */
fun Int.divFF():Int = (this + 0x7F) / 0xFF

fun alphaBlend(under:RGBA8, over:RGBA8):RGBA8 {
	val ur = under.r
	val ug = under.g
	val ub = under.b
	val ua = under.a
	val or = over.r
	val og = over.g
	val ob = over.b
	val oa = over.a
	// https://en.wikipedia.org/wiki/Alpha_compositing but purely in byte integers
	val r = (oa * or + ((0xFF - oa) * ua * ur).divFF()).divFF()
	val g = (oa * og + ((0xFF - oa) * ua * ug).divFF()).divFF()
	val b = (oa * ob + ((0xFF - oa) * ua * ub).divFF()).divFF()
	val a = 0xFF - ((0xFF - oa) * (0xFF - ua)).divFF()
	return rgba8(r, g, b, a)
}

val RGBA8.r:Byte
	get() = (this ushr 24).toByte()
val RGBA8.g:Byte
	get() = (this ushr 16).toByte()
val RGBA8.b:Byte
	get() = (this ushr 8).toByte()
val RGBA8.a:Byte
	get() = this.toByte()

/**
 * Raster image with 8 bits per channel, backed by STBImage.
 * NOTE: All instances must be disposed to prevent a memory leak.
 */
class RasterImage8 private constructor(val width:Int, val height:Int, val channels:Int, val data: ByteBuffer, private val stbData:Boolean) : Disposable {

	init {
		assert(channels in 1..4)
	}

	private var disposed = false

	/** Returns color at given position or the closest valid position (clamps). */
	fun rgba(x:Int, y:Int):RGBA8 {
		if (width == 0 || height == 0) {
			return 0
		}

		val ix = MathUtils.clamp(x, 0, width - 1)
		val iy = MathUtils.clamp(y, 0, width - 1)
		val offset = (ix + width * iy) * channels
		return when (channels) {
			1 -> {
				val gray = data.get(offset)
				rgba8(gray, gray, gray, 0xFF.toByte())
			}
			2 -> {
				val gray = data.get(offset)
				val alpha = data.get(offset + 1)
				rgba8(gray, gray, gray, alpha)
			}
			3 -> {
				val r = data.get(offset)
				val g = data.get(offset + 1)
				val b = data.get(offset + 2)
				rgba8(r, g, b, 0xFF.toByte())
			}
			4 -> {
				val r = data.get(offset)
				val g = data.get(offset + 1)
				val b = data.get(offset + 2)
				val a = data.get(offset + 3)
				rgba8(r, g, b, a)
			}
			else -> 0
		}
	}

	/** Set color of specified pixel. Does nothing if out of bounds.
	 * Red channel is used for grey. */
	fun rgba(x:Int, y:Int, rgba:RGBA8) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return
		}

		val offset = (x + width * y) * channels
		when (channels) {
			1 -> data.put(offset, rgba.r)
			2 -> {
				data.put(offset, rgba.r)
				data.put(offset + 1, rgba.a)
			}
			3 -> {
				data.put(offset, rgba.r)
				data.put(offset + 1, rgba.g)
				data.put(offset + 2, rgba.b)
			}
			4 -> {
				data.put(offset, rgba.r)
				data.put(offset + 1, rgba.g)
				data.put(offset + 2, rgba.b)
				data.put(offset + 3, rgba.a)
			}
		}
	}

	/** Modify the pixels so that the image appears on a background of given solid color. */
	fun background(color:RGBA8) {
		if (color == 0 || channels == 1 || channels == 3) {
			// If the blend won't change anything or if the image has no alpha channel
			// (because the image is opaque and the background won't show)
			return
		}

		for (y in 0 until height) {
			for (x in 0 until width) {
				val pixel = rgba(x, y)
				rgba(x, y, alphaBlend(color, pixel))
			}
		}
	}

	fun subImage(x:Int, y:Int, width:Int, height:Int):RasterImage8 {
		val x0 = maxOf(x, 0)
		val x1 = minOf(x + width, this.width)
		val y0 = maxOf(y, 0)
		val y1 = minOf(y + height, this.height)
		val newWidth = x1 - x0
		val newHeight = y1 - y0
		val newData = MemoryUtil.memAlloc(newWidth * newHeight * channels)
		val data = data
		val dataStride = width * channels
		val newDataStride = newWidth * channels
		for (iy in y0 until y1) {
			val from = dataStride * iy + x0 * channels
			val to = dataStride * iy + x1 * channels
			data.limit(to)
			data.position(from)
			newData.limit(newDataStride * (iy - y0 + 1))
			newData.position(newDataStride * (iy - y0))
			MemoryUtil.memCopy(data, newData)
		}
		data.clear()
		return RasterImage8(newWidth, newHeight, channels, newData, false)
	}

	fun scaled(newWidth: Int, newHeight: Int, scaling: ImageScaling, preMultipliedAlpha:Boolean = false, sRGB:Boolean = true):RasterImage8 {
		if (newWidth == width && newHeight == height) {
			return copy()
		}

		val scaled = RasterImage8(newWidth, newHeight, channels)
		val alphaChannelIndex = when (channels) {
			2 -> 1
			4 -> 3
			else -> STBImageResize.STBIR_ALPHA_CHANNEL_NONE
		}
		val flags = if (preMultipliedAlpha) STBImageResize.STBIR_FLAG_ALPHA_PREMULTIPLIED else 0
		val colorSpace = if (sRGB) STBImageResize.STBIR_COLORSPACE_SRGB else STBImageResize.STBIR_COLORSPACE_LINEAR

		val success = when (scaling) {
			ImageScaling.Nearest -> {
				val scaleX = width.toFloat() / newWidth
				val scaleY = height.toFloat() / newHeight
				for (y in 0 until newHeight) {
					for (x in 0 until newWidth) {
						val oX = Math.round(x * scaleX)
						val oY = Math.round(y * scaleY)
						scaled.rgba(x, y, this.rgba(oX, oY))
					}
				}
				true
			}
			ImageScaling.Bilinear -> {
				STBImageResize.stbir_resize_uint8_generic(this.data, this.width, this.height, 0, scaled.data, scaled.width, scaled.height, 0, channels, alphaChannelIndex, flags, STBImageResize.STBIR_EDGE_CLAMP, STBImageResize.STBIR_FILTER_TRIANGLE, colorSpace)
			}
			ImageScaling.Bicubic -> {
				STBImageResize.stbir_resize_uint8_generic(this.data, this.width, this.height, 0, scaled.data, scaled.width, scaled.height, 0, channels, alphaChannelIndex, flags, STBImageResize.STBIR_EDGE_CLAMP, STBImageResize.STBIR_FILTER_CUBICBSPLINE, colorSpace)
			}
			ImageScaling.Quality -> {
				STBImageResize.stbir_resize_uint8_generic(this.data, this.width, this.height, 0, scaled.data, scaled.width, scaled.height, 0, channels, alphaChannelIndex, flags, STBImageResize.STBIR_EDGE_CLAMP, STBImageResize.STBIR_FILTER_MITCHELL, colorSpace)
			}
		}

		if (!success) {
			Log.error("RasterImage8", "Failed to rescale $this to $scaled")
		}

		return scaled
	}

	fun copy():RasterImage8 {
		return RasterImage8(width, height, channels, MemoryUtil.memDuplicate(data), false)
	}

	override fun dispose() {
		if (disposed) {
			return
		}
		disposed = true
		if (stbData) {
			STBImage.stbi_image_free(data)
		} else {
			MemoryUtil.memFree(data)
		}
	}

	companion object {
		/** Load image, return null and log the problem on error. */
		operator fun invoke(path:String):RasterImage8? {
			val w = IntArray(1)
			val h = IntArray(1)
			val channels = IntArray(1)
			val data = STBImage.stbi_load(path, w, h, channels, 0) ?: run {
				Log.error("RasterImage8", "Failed to load '$path': ${STBImage.stbi_failure_reason()}")
				return null
			}
			return RasterImage8(w[0], h[0], channels[0], data, true)
		}

		operator fun invoke(width:Int, height:Int, channels:Int):RasterImage8 {
			return RasterImage8(width, height, channels, MemoryUtil.memAlloc(width * height * channels), false)
		}
	}
}