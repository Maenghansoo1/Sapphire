package com.sapphire.hawe.feature.draw

import java.io.File
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class InkPreprocessorTest {
    private val goldenNames = listOf("01_empty", "02_too_few", "03_minimum_ink", "04_horizontal_line", "05_vertical_line",
        "06_full", "07_diagonal", "08_circle_off_center", "09_rect_outline", "10_cat_drawing")

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResource("/quickdraw/$path")) { "$path 없음" }.readText()

    /** M1 골든 예시: 마스크를 전처리하면 기대값과 모든 칸이 1e-3 안(명세 "검증"). */
    @Test fun matchesGoldenExamplesFromTraining() {
        goldenNames.forEach { name ->
            val golden = Json.parseToJsonElement(resource("golden/$name.json")).jsonObject
            val width = golden["width"]!!.jsonPrimitive.int
            val height = golden["height"]!!.jsonPrimitive.int
            val rows = golden["mask"]!!.jsonArray.map { it.jsonPrimitive.content }
            val mask = BooleanArray(width * height) { rows[it / width][it % width] == '1' }
            val actual = InkPreprocessor.preprocess(mask, width, height)
            val expected = golden["expected"]!!
            if (expected is JsonNull) {
                assertNull("$name 은 너무 비어서 멈춰야 해요", actual)
            } else {
                val values = (expected as JsonArray).flatMap { row -> row.jsonArray.map { it.jsonPrimitive.float } }
                assertNotNull(name, actual)
                assertEquals(28 * 28, values.size)
                val worst = values.indices.maxOf { abs(values[it] - actual!![it]) }
                assertTrue("$name 최대 오차 $worst", worst <= 1e-3f)
                assertTrue(name, actual!!.all { it in 0f..1f })
                assertEquals("$name 가장 진한 값은 1", 1f, actual.max(), 1e-6f)
            }
        }
    }

    @Test fun inkRuleFollowsSpec() {
        fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
        val pixels = intArrayOf(
            argb(255, 255, 255, 255),  // 흰 배경: 아님
            argb(0, 0, 0, 0),          // 투명 검정: 아님
            argb(255, 17, 17, 17),     // 검정 잉크
            argb(255, 0xF2, 0xD7, 0x4E),  // 팔레트 연노랑(min 78): 잉크
            argb(255, 240, 240, 240),  // 거의 흰색: 아님
            argb(127, 0, 0, 0),        // 반투명(127): 아님
            argb(128, 0, 0, 0),        // 알파 128: 잉크
            argb(255, 234, 250, 250),  // min 234 < 235: 잉크
            argb(255, 235, 250, 250),  // min 235: 아님
        )
        assertEquals(listOf(false, false, true, true, false, false, true, true, false),
            InkPreprocessor.inkMask(pixels, pixels.size, 1).toList())
        // 앱 팔레트 7색은 모두 잉크(흰 배경 PNG 에서 연한 색이 사라지지 않음)
        val palette = com.sapphire.hawe.feature.mission.DrawPalette.colors.toIntArray()
        assertTrue(InkPreprocessor.inkMask(palette, palette.size, 1).all { it })
    }

    @Test fun minInkAndAreaWeights() {
        assertEquals(3, InkPreprocessor.minInk(32, 32))
        assertEquals(3, InkPreprocessor.minInk(77, 77))  // 5929/2000 → 올림 3
        assertEquals(61, InkPreprocessor.minInk(320, 380))  // 121600/2000 = 60.8 → 61
        listOf(1, 7, 20, 37, 64, 700).forEach { side ->
            InkPreprocessor.areaWeights(side, 20).forEach { row ->
                assertEquals("S=$side 한 칸의 가중치 합", 1.0, row.sumOf { it.second }, 1e-9)
            }
        }
    }

    @Test fun positionAndSizeDoNotMatterButShapeDoes() {
        // 같은 가로줄을 다른 곳·다른 크기 캔버스에 그려도 결과가 같아요(명세 (3)(5)(6))
        fun line(width: Int, height: Int, x0: Int, y: Int, length: Int) =
            BooleanArray(width * height) { val x = it % width; it / width == y && x in x0 until x0 + length }
        val a = InkPreprocessor.preprocess(line(60, 40, 5, 10, 30), 60, 40)!!
        val b = InkPreprocessor.preprocess(line(100, 100, 50, 90, 30), 100, 100)!!
        assertArrayEquals(a, b, 1e-6f)
        // 가로줄은 세로 가운데(28칸 중 위아래 여백)에 놓여요 — 종횡비 유지
        val rowsWithInk = (0 until 28).filter { y -> (0 until 28).any { x -> a[y * 28 + x] > 0f } }
        assertTrue(rowsWithInk.all { it in 13..14 })
    }

    /** android 쪽 복사본이 M1 원본과 같아야 해요(명세·모델을 다시 만들면 같이 바꾸도록). */
    @Test fun copiesMatchMlOriginals() {
        val ml = File("../../ml/quickdraw")
        if (!ml.isDirectory) return  // 저장소 밖에서 돌릴 때
        goldenNames.forEach { name ->
            assertEquals("golden/$name.json", File(ml, "golden/$name.json").readText(), resource("golden/$name.json"))
        }
        assertEquals(File(ml, "export/golden_outputs.json").readText(), resource("golden_outputs.json"))
        assertArrayEquals(File(ml, "export/quickdraw_cnn.tflite").readBytes(),
            File("src/main/assets/quickdraw/quickdraw_cnn.tflite").readBytes())
        assertEquals(File(ml, "categories.json").readText(), File("src/main/assets/quickdraw/categories.json").readText())
        // 범주 순서 = 모델 출력 순서 = labels_ko.txt 줄 순서
        val categories = parseDrawCategories(File("src/main/assets/quickdraw/categories.json").readText())
        assertEquals(File(ml, "export/labels_ko.txt").readLines().filter { it.isNotBlank() }, categories.map { it.ko })
        // "잘 모르겠어요" 기준도 모델 설명서와 같아야 해요(M1 에서 다시 정하면 앱도 같이 바꾸도록)
        val unsure = Json.parseToJsonElement(File(ml, "export/export.json").readText()).jsonObject["unsure_threshold"]!!
        assertEquals(unsure.jsonPrimitive.float, AI_GUESS_THRESHOLD, 1e-6f)
    }
}
