package com.sapphire.hawe.feature.draw

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter

/**
 * 그림 인식 모델(M1 `quickdraw_cnn.tflite`, 범주 50개)을 기기 안에서만 돌려요. 그림·결과는 어디에도 보내거나 남기지 않아요.
 * 처음 쓸 때 한 번만 불러오고(앱 시작 비용 없음), 불러오지 못하면 기능을 숨겨요(크래시 없음).
 */
class QuickDrawClassifier private constructor(private val interpreter: Interpreter, val categories: List<DrawCategory>) {
    private val mutex = Mutex()  // Interpreter 는 동시에 부르면 안 돼요
    private val input = ByteBuffer.allocateDirect(INPUT_SIZE * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(categories.size) }

    /** 전처리한 28×28 입력([InkPreprocessor.preprocess]) → 범주별 확률(합 1). */
    suspend fun classify(pixels: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        require(pixels.size == INPUT_SIZE) { "입력은 28×28 이어야 해요" }
        mutex.withLock {
            input.rewind()
            pixels.forEach { input.putFloat(it) }
            input.rewind()
            interpreter.run(input, output)
            output[0].copyOf()
        }
    }

    companion object {
        private const val TAG = "QuickDrawClassifier"
        private const val MODEL = "quickdraw/quickdraw_cnn.tflite"
        private const val CATEGORIES = "quickdraw/categories.json"
        private const val INPUT_SIZE = InkPreprocessor.OUTPUT_SIZE * InkPreprocessor.OUTPUT_SIZE
        private val loadLock = Mutex()
        @Volatile private var loaded: QuickDrawClassifier? = null
        @Volatile private var unavailable = false

        /** 모델을 쓸 수 있으면 그 인스턴스, 자산 누락·손상·범주 수 불일치면 null(다시 시도하지 않아요). */
        suspend fun get(context: Context): QuickDrawClassifier? {
            loaded?.let { return it }
            if (unavailable) return null
            return loadLock.withLock {
                loaded ?: if (unavailable) null else try {
                    withContext(Dispatchers.IO) { load(context.applicationContext) }.also { loaded = it }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    unavailable = true
                    Log.w(TAG, "그림 인식 모델을 불러오지 못했어요: ${error.javaClass.simpleName}")
                    null
                }
            }
        }

        private fun load(context: Context): QuickDrawClassifier {
            val assets = context.assets
            val categories = parseDrawCategories(assets.open(CATEGORIES).bufferedReader().use { it.readText() })
            val bytes = assets.open(MODEL).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply { put(bytes); rewind() }
            val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
            val inputShape = interpreter.getInputTensor(0).shape()
            val outputShape = interpreter.getOutputTensor(0).shape()
            if (!inputShape.contentEquals(intArrayOf(1, InkPreprocessor.OUTPUT_SIZE, InkPreprocessor.OUTPUT_SIZE, 1)) ||
                !outputShape.contentEquals(intArrayOf(1, categories.size))) {
                interpreter.close()
                error("모델 입출력 모양이 범주와 맞지 않아요: ${inputShape.toList()} → ${outputShape.toList()}")
            }
            return QuickDrawClassifier(interpreter, categories)
        }
    }
}
