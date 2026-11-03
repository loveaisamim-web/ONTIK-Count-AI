package com.ontik.countai.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * High-speed conversion of CameraX 1280x720 RGBA_8888 [ImageProxy] frames into an upright
 * downscaled working [Bitmap] (640x360) for 30+ FPS OpenCV processing.
 */
object ImageProxyUtils {

    private var reusableRawBitmap: Bitmap? = null

    @Synchronized
    fun imageProxyToUprightBitmap(imageProxy: ImageProxy, isFrontCamera: Boolean): Bitmap? {
        return try {
            val plane = imageProxy.planes.firstOrNull() ?: return null
            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val buffer = plane.buffer
            buffer.rewind()

            var rawBitmap = reusableRawBitmap
            if (rawBitmap == null || rawBitmap.width != width || rawBitmap.height != height) {
                rawBitmap?.recycle()
                rawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                reusableRawBitmap = rawBitmap
            }

            if (rowStride == width * pixelStride) {
                rawBitmap.copyPixelsFromBuffer(buffer)
            } else {
                val rowBytes = ByteArray(rowStride)
                val pixels = IntArray(width * height)
                for (y in 0 until height) {
                    val length = minOf(rowStride, buffer.remaining())
                    buffer.get(rowBytes, 0, length)
                    var offset = 0
                    for (x in 0 until width) {
                        val r = rowBytes[offset].toInt() and 0xFF
                        val g = rowBytes[offset + 1].toInt() and 0xFF
                        val b = rowBytes[offset + 2].toInt() and 0xFF
                        val a = rowBytes[offset + 3].toInt() and 0xFF
                        pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        offset += pixelStride
                    }
                }
                rawBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            // Scale by 0.5x during rotation so 1280x720 becomes 360x640 upright for 30+ FPS OpenCV analysis
            val matrix = Matrix().apply {
                postScale(0.5f, 0.5f)
                if (rotationDegrees != 0) {
                    postRotate(rotationDegrees.toFloat())
                }
                if (isFrontCamera) {
                    postScale(-1f, 1f)
                }
            }

            Bitmap.createBitmap(
                rawBitmap,
                0,
                0,
                rawBitmap.width,
                rawBitmap.height,
                matrix,
                true
            )
        } catch (_: Exception) {
            null
        }
    }
}
