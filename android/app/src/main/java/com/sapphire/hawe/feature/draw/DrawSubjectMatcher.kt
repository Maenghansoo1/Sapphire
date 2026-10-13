package com.sapphire.hawe.feature.draw

import com.sapphire.hawe.core.model.MissionSpec
import com.sapphire.hawe.core.model.MissionTemplates
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 모델 범주 하나(`assets/quickdraw/categories.json`, M1). [id] 가 모델 출력의 번호예요. */
@Serializable
data class DrawCategory(val id: Int, val quickdraw: String, val ko: String,
    @SerialName("aliases_ko") val aliasesKo: List<String> = emptyList()) {
    val names: List<String> get() = listOf(ko) + aliasesKo
}

/** categories.json 을 읽어요. 번호가 0부터 빠짐없이 이어지지 않으면 모델 출력과 맞출 수 없어서 실패해요. */
fun parseDrawCategories(json: String): List<DrawCategory> {
    val categories = Json { ignoreUnknownKeys = true }.decodeFromString<List<DrawCategory>>(json).sortedBy { it.id }
    require(categories.isNotEmpty() && categories.withIndex().all { (index, category) -> category.id == index }) {
        "범주 번호가 0부터 이어지지 않아요"
    }
    return categories
}

/**
 * 미션 주제가 모델 범주 중 무엇인지 찾아요. 찾으면 "AI가 맞혔는지" 판정하고, 못 찾으면 추측만 보여요.
 * - 30초 스케치는 빈칸 `subject`(없으면 주제 문장), A를 B로는 결과물인 `b`(없으면 주제 문장)로 판정해요.
 * - 사람(`[대상]`)을 그리는 스케치는 범주에 없으니 판정하지 않아요.
 * - 한 글자 범주(해·달·별·산·차·개…)는 다른 낱말 속 글자와 헷갈리기 쉬워서 낱말이 통째로 같을 때만 맞다고 봐요.
 */
object DrawSubjectMatcher {
    private val particles = listOf("으로", "이랑", "랑", "을", "를", "이", "가", "은", "는", "의", "로", "와", "과", "도", "에")

    fun match(template: String, topic: String, spec: MissionSpec, categories: List<DrawCategory>): DrawCategory? {
        val text = when (template) {
            MissionTemplates.DRAW_A_TO_B -> spec.drawTo?.takeIf { it.isNotBlank() } ?: topic
            else -> {
                if (spec.hasTarget) return null
                spec.subject?.takeIf { it.isNotBlank() } ?: topic
            }
        }
        return matchText(text, categories)
    }

    internal fun matchText(text: String, categories: List<DrawCategory>): DrawCategory? {
        val whole = normalize(text)
        if (whole.isEmpty()) return null
        val words = text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            .flatMap { word -> listOf(normalize(word), normalize(stripParticle(word))) }.toSet()
        // 이름이 길수록 구체적이에요("생일케이크" > "케이크"). 같은 길이면 범주 번호 순.
        val candidates = categories.flatMap { category -> category.names.map { normalize(it) to category } }
            .filter { (name, _) -> name.isNotEmpty() }
            .sortedByDescending { (name, _) -> name.length }
        return candidates.firstOrNull { (name, _) -> name == whole || name in words }?.second
            ?: candidates.firstOrNull { (name, _) -> name.length >= 2 && name in whole }?.second
    }

    private fun normalize(value: String) = value.filterNot { it.isWhitespace() }.lowercase()

    private fun stripParticle(word: String): String =
        particles.firstOrNull { word.length > it.length && word.endsWith(it) }?.let { word.dropLast(it.length) } ?: word
}
