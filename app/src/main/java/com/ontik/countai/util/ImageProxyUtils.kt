package com.ontik.countai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface

/**
 * Memory-efficient utilities for converting CameraX [ImageProxy] frames and gallery [Uri] images
 * into upright ARGB_8888 [Bitmap] instances ready for TensorFlow Lite inference, suitable for low-RAM phones.
 */
object ImageProxyUtils {

    /**
     * Converts an `OUTPUT_IMAGE_FORMAT_RGBA_8888` [ImageProxy] into an upright rotated [Bitmap].
     */
    fun imageProxyToUprightBitmap(imageProxy: ImageProxy, isFrontCamera: Boolean): Bitmap? {
        return try {
            val plane = imageProxy.planes.firstOrNull() ?: return null
            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val buffer = plane.buffer
            buffer.rewind()

            val rawBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            if (rowStride == width * pixelStride) {
                rawBitmap.copyPixelsFromBuffer(buffer)
            } else {
                // Handle row padding stride on certain hardware ISP pipelines
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
            if (rotationDegrees == 0 && !isFrontCamera) {
                rawBitmap
            } else {
                val matrix = Matrix().apply {
                    if (rotationDegrees != 0) {
                        postRotate(rotationDegrees.toFloat())
                    }
                    if (isFrontCamera) {
                        postScale(-1f, 1f)
                    }
                }
                val rotated = Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    rawBitmap.width,
                    rawBitmap.height,
                    matrix,
                    true
                )
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Loads and downsamples a photo from a [Uri] with EXIF orientation applied so large 12MP-108MP
     * camera photos never trigger OutOfMemoryError on low-RAM Android devices.
     */
    fun loadUprightBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 1280): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDimension ||
                boundsOptions.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            val exifOrientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            if (matrix.isIdentity) {
                decoded
            } else {
                val rotated = Bitmap.createBitmap(
                    decoded,
                    0,
                    0,
                    decoded.width,
                    decoded.height,
                    matrix,
                    true
                )
                if (rotated != decoded) decoded.recycle()
                rotated
            }
        } catch (e: Exception) {
            null
        }
    }
}
