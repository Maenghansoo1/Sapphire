package com.sapphire.hawe.feature.draw

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sapphire.hawe.feature.mission.DrawStroke
import com.sapphire.hawe.feature.mission.StrokePoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 기기(에뮬레이터)에서 실제 LiteRT 로 모델을 돌려 M1 골든 출력과 비교해요. 골든 파일은 test/resources 를 같이 써요. */
@RunWith(AndroidJUnit4::class)
class QuickDrawClassifierTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private fun asset(path: String) = testAssets.open("quickdraw/$path").bufferedReader().use { it.readText() }
    private fun classifier() = runBlocking { QuickDrawClassifier.get(app) }!!

    /** 모델 설명서의 약속: 골든 입력의 모든 확률 차이 ≤ 0.02, 1·2등 차이(margin) ≥ 0.05 이면 1등도 같음. */
    @Test fun matchesGoldenOutputs() = runBlocking {
        val classifier = classifier()
        assertEquals(50, classifier.categories.size)
        val items = Json.parseToJsonElement(asset("golden_outputs.json")).jsonObject["items"]!!.jsonArray
        assertEquals(8, items.size)
        items.forEach { element ->
            val item = element.jsonObject
            val name = item["name"]!!.jsonPrimitive.content
            val golden = Json.parseToJsonElement(asset("golden/$name.json")).jsonObject
            val input = (golden["expected"] as JsonArray).flatMap { row -> row.jsonArray.map { it.jsonPrimitive.float } }
            val expected = item["probabilities"]!!.jsonArray.map { it.jsonPrimitive.float }
            val actual = classifier.classify(input.toFloatArray())
            val worst = expected.indices.maxOf { abs(expected[it] - actual[it]) }
            assertTrue("$name 확률 최대 차이 $worst", worst <= 0.02f)
            assertEquals("$name 확률 합", 1f, actual.sum(), 0.02f)
            if (item["margin"]!!.jsonPrimitive.float >= 0.05f) {
                val top = item["top3"]!!.jsonArray[0].jsonObject["index"]!!.jsonPrimitive.int
                assertEquals("$name 1등", top, actual.indices.maxBy { actual[it] })
            }
        }
    }

    /** 마스크 → 전처리 → 모델 전체 흐름: 골든 고양이 그림이 1등 '고양이'. */
    @Test fun catMaskEndToEnd() = runBlocking {
        val golden = Json.parseToJsonElement(asset("golden/10_cat_drawing.json")).jsonObject
        val width = golden["width"]!!.jsonPrimitive.int
        val height = golden["height"]!!.jsonPrimitive.int
        val rows = golden["mask"]!!.jsonArray.map { it.jsonPrimitive.content }
        val mask = BooleanArray(width * height) { rows[it / width][it % width] == '1' }
        val input = InkPreprocessor.preprocess(mask, width, height)!!
        val classifier = classifier()
        val top = topGuesses(classifier.classify(input), classifier.categories)
        assertEquals("고양이", top.first().category.ko)
        assertTrue(top.first().probability > 0.9f)
    }

    /** 내가 그린 획 → 투명 배경 검정 잉크 → 잉크 판정. 연한 색으로 그려도 잉크이고, 지우개로 지우면 사라져요. */
    @Test fun strokesBecomeInkAndEraserRemovesIt() {
        val circle = (0..40).map { step ->
            val angle = step / 40.0 * 2 * Math.PI
            StrokePoint((100 + 60 * cos(angle)).toFloat(), (100 + 60 * sin(angle)).toFloat())
        }
        val yellow = DrawStroke(circle, 0xFFF2D74E.toInt(), 6f, isEraser = false)
        val image = strokeInkImage(listOf(yellow), 200f, 200f)
        assertEquals(200, image.width)
        val inkCount = InkPreprocessor.inkMask(image.argb, image.width, image.height).count { it }
        assertTrue("잉크 칸 $inkCount", inkCount > InkPreprocessor.minInk(200, 200))
        assertNotNull(image.modelInput())
        val eraser = DrawStroke(circle, 0, 30f, isEraser = true)
        assertNull("지운 그림은 너무 비어 있음", strokeInkImage(listOf(yellow, eraser), 200f, 200f).modelInput())
        // 아주 작은 점 하나도 너무 비어 있음
        val dot = DrawStroke(listOf(StrokePoint(50f, 50f)), 0xFF111111.toInt(), 3f, isEraser = false)
        assertNull(strokeInkImage(listOf(dot), 300f, 380f).modelInput())
    }
}
