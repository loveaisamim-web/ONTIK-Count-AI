package com.ontik.countai.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

/**
 * High-speed conversion of CameraX 1280x720 RGBA_8888 [ImageProxy] frames into an upright
 * downscaled working [Bitmap] (360x640) for 30+ FPS OpenCV processing.
 * Reuses intermediate row/pixel buffers to eliminate per-frame 3.6MB GC allocations (P7 fix).
 */
object ImageProxyUtils {

    private var reusableRawBitmap: Bitmap? = null
    private var reusableRowBytes: ByteArray? = null
    private var reusablePixels: IntArray? = null

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
                var rowBytes = reusableRowBytes
                if (rowBytes == null || rowBytes.size < rowStride) {
                    rowBytes = ByteArray(rowStride)
                    reusableRowBytes = rowBytes
                }
                val totalPixels = width * height
                var pixels = reusablePixels
                if (pixels == null || pixels.size < totalPixels) {
                    pixels = IntArray(totalPixels)
                    reusablePixels = pixels
                }

                for (y in 0 until height) {
                    val length = minOf(rowStride, buffer.remaining())
                    buffer.get(rowBytes, 0, length)
                    var offset = 0
                    val rowStart = y * width
                    for (x in 0 until width) {
                        val r = rowBytes[offset].toInt() and 0xFF
                        val g = rowBytes[offset + 1].toInt() and 0xFF
                        val b = rowBytes[offset + 2].toInt() and 0xFF
                        val a = rowBytes[offset + 3].toInt() and 0xFF
                        pixels[rowStart + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        offset += pixelStride
                    }
                }
                rawBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            }

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
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
