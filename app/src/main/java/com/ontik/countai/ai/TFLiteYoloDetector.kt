package com.ontik.countai.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.ontik.countai.domain.model.DetectionResult
import com.ontik.countai.domain.model.ModelEngineState
import com.ontik.countai.domain.model.ModelProfile
import com.ontik.countai.domain.model.NormalizedRect
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class TFLiteYoloDetector(
    private val context: Context,
    private val profile: ModelProfile,
    private val customModelFilePath: String? = null,
    private val numThreads: Int = 4
) : ObjectDetectorContract {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()

    private var inputWidth: Int = profile.inputResolution
    private var inputHeight: Int = profile.inputResolution
    private var isInputQuantized: Boolean = false

    private var inputByteBuffer: ByteBuffer? = null
    private var intValues: IntArray? = null

    override var engineState: ModelEngineState = ModelEngineState.Uninitialized
        private set

    init {
        initializeInterpreter()
    }

    private fun initializeInterpreter() {
        try {
            labels = loadLabels(profile.labelAssetFile)

            if (profile == ModelProfile.BUILTIN_SMALL_OBJECT_VISION) {
                engineState = ModelEngineState.Ready(
                    modelName = "Built-In Smart Vision Engine",
                    inputWidth = 240,
                    inputHeight = 240,
                    isQuantized = false,
                    outputShapeDescription = "Adaptive Contrast + Connected-Component Segmentation"
                )
                return
            }

            val modelBufferAndName = loadModelByteBuffer()
            if (modelBufferAndName == null) {
                engineState = ModelEngineState.FallbackBuiltInReady(
                    reasonEn = "Built-In Vision Engine Active (Ready to count objects! You can also import a custom .tflite model in Settings)",
                    reasonBn = "বিল্ট-ইন ভিশন ইঞ্জিন চালু আছে (বস্তু গণনার জন্য প্রস্তুত! চাইলে সেটিংস থেকে কাস্টম .tflite মডেলও যোগ করতে পারেন)"
                )
                return
            }

            val (mappedBuffer, modelDisplayName) = modelBufferAndName
            val options = Interpreter.Options().apply {
                setNumThreads(numThreads.coerceIn(1, 8))
                setUseXNNPACK(true)
            }

            val tflite = Interpreter(mappedBuffer, options)
            interpreter = tflite

            val inputTensor = tflite.getInputTensor(0)
            val inputShape = inputTensor.shape()
            if (inputShape.size >= 4) {
                if (inputShape[1] > 0 && inputShape[2] > 0) {
                    inputHeight = inputShape[1]
                    inputWidth = inputShape[2]
                }
            }
            isInputQuantized = inputTensor.dataType() == DataType.UINT8 || inputTensor.dataType() == DataType.INT8

            val bytesPerChannel = if (isInputQuantized) 1 else 4
            val bufferSize = 1 * inputWidth * inputHeight * 3 * bytesPerChannel
            inputByteBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
                order(ByteOrder.nativeOrder())
            }
            intValues = IntArray(inputWidth * inputHeight)

            val outputTensorCount = tflite.outputTensorCount
            val outShapeDesc = (0 until outputTensorCount).joinToString("; ") { idx ->
                val t = tflite.getOutputTensor(idx)
                "Out$idx=${t.shape().contentToString()}"
            }

            engineState = ModelEngineState.Ready(
                modelName = modelDisplayName,
                inputWidth = inputWidth,
                inputHeight = inputHeight,
                isQuantized = isInputQuantized,
                outputShapeDescription = outShapeDesc
            )
        } catch (e: Exception) {
            engineState = ModelEngineState.FallbackBuiltInReady(
                reasonEn = "Using Built-In Vision Engine (${e.localizedMessage ?: "fallback"})",
                reasonBn = "বিল্ট-ইন ভিশন ইঞ্জিন ব্যবহৃত হচ্ছে"
            )
        }
    }

    private fun loadLabels(assetPath: String): List<String> {
        return try {
            context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream)).readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            listOf("seed", "grain", "bean", "object")
        }
    }

    private fun loadModelByteBuffer(): Pair<MappedByteBuffer, String>? {
        if (!customModelFilePath.isNullOrBlank()) {
            val file = File(customModelFilePath)
            if (file.exists() && file.length() > 64) {
                FileInputStream(file).use { fis ->
                    val channel = fis.channel
                    val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                    return mapped to file.name
                }
            }
        }

        val fallbackLocalFile = File(context.filesDir, "models/custom_imported_model.tflite")
        if (fallbackLocalFile.exists() && fallbackLocalFile.length() > 64) {
            FileInputStream(fallbackLocalFile).use { fis ->
                val channel = fis.channel
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                return mapped to fallbackLocalFile.name
            }
        }

        if (profile.defaultAssetFile.isNotBlank()) {
            try {
                val afd = context.assets.openFd(profile.defaultAssetFile)
                FileInputStream(afd.fileDescriptor).use { fis ->
                    val channel = fis.channel
                    val mapped = channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        afd.startOffset,
                        afd.declaredLength
                    )
                    return mapped to profile.defaultAssetFile.substringAfterLast('/')
                }
            } catch (_: Exception) {
            }
        }

        return null
    }

    @Synchronized
    override fun detect(
        bitmap: Bitmap,
        confidenceThreshold: Float,
        iouThreshold: Float,
        countingZone: NormalizedRect?
    ): InferenceOutput {
        val currentInterpreter = interpreter
        if (currentInterpreter == null) {
            return BuiltInSmallObjectDetector.detectObjects(
                bitmap = bitmap,
                confidenceThreshold = confidenceThreshold,
                iouThreshold = iouThreshold,
                countingZone = countingZone
            )
        }

        val startTime = SystemClock.elapsedRealtime()
        try {
            val resized = if (bitmap.width == inputWidth && bitmap.height == inputHeight) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
            }

            val buffer = inputByteBuffer ?: return BuiltInSmallObjectDetector.detectObjects(
                bitmap, confidenceThreshold, iouThreshold, countingZone
            )
            val pixels = intValues ?: return BuiltInSmallObjectDetector.detectObjects(
                bitmap, confidenceThreshold, iouThreshold, countingZone
            )
            buffer.rewind()

            resized.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
            if (resized !== bitmap) {
                resized.recycle()
            }

            if (isInputQuantized) {
                for (pixel in pixels) {
                    buffer.put(((pixel shr 16) and 0xFF).toByte())
                    buffer.put(((pixel shr 8) and 0xFF).toByte())
                    buffer.put((pixel and 0xFF).toByte())
                }
            } else {
                for (pixel in pixels) {
                    buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                    buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                    buffer.putFloat((pixel and 0xFF) / 255.0f)
                }
            }

            val outputCount = currentInterpreter.outputTensorCount
            val rawCandidates = mutableListOf<DetectionResult>()
            var lowConfCount = 0
            val lowConfFloor = (confidenceThreshold * 0.55f).coerceAtLeast(0.10f)

            if (outputCount >= 4) {
                val numDetectionsShape = currentInterpreter.getOutputTensor(0).shape()
                val maxDetections = if (numDetectionsShape.size >= 2) numDetectionsShape[1] else 25
                val locations = Array(1) { Array(maxDetections) { FloatArray(4) } }
                val labelIndices = Array(1) { FloatArray(maxDetections) }
                val scores = Array(1) { FloatArray(maxDetections) }
                val countArr = FloatArray(1)

                val outputMap = mapOf(
                    0 to locations,
                    1 to labelIndices,
                    2 to scores,
                    3 to countArr
                )
                currentInterpreter.runForMultipleInputsOutputs(arrayOf(buffer), outputMap)

                val total = countArr[0].toInt().coerceIn(0, maxDetections)
                for (i in 0 until total) {
                    val score = scores[0][i]
                    if (score < confidenceThreshold) {
                        if (score >= lowConfFloor) lowConfCount++
                        continue
                    }
                    val box = locations[0][i]
                    val top = box[0].coerceIn(0f, 1f)
                    val left = box[1].coerceIn(0f, 1f)
                    val bottom = box[2].coerceIn(0f, 1f)
                    val right = box[3].coerceIn(0f, 1f)
                    if (right <= left || bottom <= top) continue

                    val clsIdx = labelIndices[0][i].toInt().coerceAtLeast(0)
                    val label = labels.getOrElse(clsIdx) { "object" }
                    val rect = NormalizedRect(left, top, right, bottom)
                    if (countingZone == null || countingZone.contains(rect.centerX, rect.centerY)) {
                        rawCandidates.add(
                            DetectionResult(
                                boundingBox = rect,
                                confidence = score,
                                classIndex = clsIdx,
                                label = label
                            )
                        )
                    }
                }
            } else {
                val outTensor = currentInterpreter.getOutputTensor(0)
                val shape = outTensor.shape()
                val dim1 = if (shape.size >= 2) shape[1] else 0
                val dim2 = if (shape.size >= 3) shape[2] else 0

                if (dim1 > 0 && dim2 > 0) {
                    val outputArray = Array(1) { Array(dim1) { FloatArray(dim2) } }
                    currentInterpreter.run(buffer, outputArray)
                    val matrix = outputArray[0]

                    if (dim1 < dim2) {
                        val numClasses = dim1 - 4
                        val numAnchors = dim2
                        for (anchor in 0 until numAnchors) {
                            var maxClassScore = 0f
                            var bestClassIndex = 0
                            for (c in 0 until numClasses) {
                                val score = matrix[4 + c][anchor]
                                if (score > maxClassScore) {
                                    maxClassScore = score
                                    bestClassIndex = c
                                }
                            }
                            if (maxClassScore < confidenceThreshold) {
                                if (maxClassScore >= lowConfFloor) lowConfCount++
                                continue
                            }

                            var cx = matrix[0][anchor]
                            var cy = matrix[1][anchor]
                            var w = matrix[2][anchor]
                            var h = matrix[3][anchor]

                            if (cx > 1.5f || cy > 1.5f || w > 1.5f || h > 1.5f) {
                                cx /= inputWidth.toFloat()
                                cy /= inputHeight.toFloat()
                                w /= inputWidth.toFloat()
                                h /= inputHeight.toFloat()
                            }

                            val left = (cx - w * 0.5f).coerceIn(0f, 1f)
                            val top = (cy - h * 0.5f).coerceIn(0f, 1f)
                            val right = (cx + w * 0.5f).coerceIn(0f, 1f)
                            val bottom = (cy + h * 0.5f).coerceIn(0f, 1f)

                            if (right - left < 0.004f || bottom - top < 0.004f) continue
                            val rect = NormalizedRect(left, top, right, bottom)
                            if (countingZone == null || countingZone.contains(rect.centerX, rect.centerY)) {
                                rawCandidates.add(
                                    DetectionResult(
                                        boundingBox = rect,
                                        confidence = maxClassScore,
                                        classIndex = bestClassIndex,
                                        label = labels.getOrElse(bestClassIndex) { "seed" }
                                    )
                                )
                            }
                        }
                    } else {
                        val numAnchors = dim1
                        val channels = dim2
                        for (anchor in 0 until numAnchors) {
                            val row = matrix[anchor]
                            val score: Float
                            val bestClassIndex: Int

                            if (channels == 6) {
                                score = row[4]
                                bestClassIndex = row[5].toInt().coerceAtLeast(0)
                            } else {
                                val objConf = row[4]
                                if (objConf < lowConfFloor) continue
                                var maxCls = 0f
                                var bestIdx = 0
                                for (c in 5 until channels) {
                                    if (row[c] > maxCls) {
                                        maxCls = row[c]
                                        bestIdx = c - 5
                                    }
                                }
                                score = objConf * maxCls
                                bestClassIndex = bestIdx
                            }

                            if (score < confidenceThreshold) {
                                if (score >= lowConfFloor) lowConfCount++
                                continue
                            }

                            var cx = row[0]
                            var cy = row[1]
                            var w = row[2]
                            var h = row[3]

                            if (cx > 1.5f || cy > 1.5f || w > 1.5f || h > 1.5f) {
                                cx /= inputWidth.toFloat()
                                cy /= inputHeight.toFloat()
                                w /= inputWidth.toFloat()
                                h /= inputHeight.toFloat()
                            }

                            val left = (cx - w * 0.5f).coerceIn(0f, 1f)
                            val top = (cy - h * 0.5f).coerceIn(0f, 1f)
                            val right = (cx + w * 0.5f).coerceIn(0f, 1f)
                            val bottom = (cy + h * 0.5f).coerceIn(0f, 1f)

                            if (right - left < 0.004f || bottom - top < 0.004f) continue
                            val rect = NormalizedRect(left, top, right, bottom)
                            if (countingZone == null || countingZone.contains(rect.centerX, rect.centerY)) {
                                rawCandidates.add(
                                    DetectionResult(
                                        boundingBox = rect,
                                        confidence = score,
                                        classIndex = bestClassIndex,
                                        label = labels.getOrElse(bestClassIndex) { "seed" }
                                    )
                                )
                            }
                        }
                    }
                }
            }

            val nmsFiltered = applyNonMaximumSuppression(rawCandidates, iouThreshold)
            val elapsed = SystemClock.elapsedRealtime() - startTime

            return InferenceOutput(
                detections = nmsFiltered,
                lowConfidenceCandidateCount = lowConfCount,
                inferenceTimeMs = elapsed,
                statusMessage = null
            )
        } catch (e: Exception) {
            return BuiltInSmallObjectDetector.detectObjects(
                bitmap = bitmap,
                confidenceThreshold = confidenceThreshold,
                iouThreshold = iouThreshold,
                countingZone = countingZone
            )
        }
    }

    private fun applyNonMaximumSuppression(
        candidates: List<DetectionResult>,
        iouThreshold: Float
    ): List<DetectionResult> {
        if (candidates.isEmpty()) return emptyList()
        val sorted = candidates.sortedByDescending { it.confidence }
        val selected = ArrayList<DetectionResult>(min(sorted.size, 300))
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            val primary = sorted[i]
            selected.add(primary)
            if (selected.size >= 300) break

            for (j in (i + 1) until sorted.size) {
                if (!active[j]) continue
                val iou = computeIoU(primary.boundingBox, sorted[j].boundingBox)
                if (iou > iouThreshold) {
                    active[j] = false
                }
            }
        }
        return selected
    }

    private fun computeIoU(a: NormalizedRect, b: NormalizedRect): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interWidth = (interRight - interLeft).coerceAtLeast(0f)
        val interHeight = (interBottom - interTop).coerceAtLeast(0f)
        val interArea = interWidth * interHeight
        if (interArea <= 0f) return 0f

        val areaA = a.width * a.height
        val areaB = b.width * b.height
        val unionArea = areaA + areaB - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    @Synchronized
    override fun close() {
        try {
            interpreter?.close()
        } catch (_: Exception) {
        } finally {
            interpreter = null
        }
    }
}
