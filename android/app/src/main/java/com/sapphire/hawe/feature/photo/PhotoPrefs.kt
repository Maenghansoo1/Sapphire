package com.sapphire.hawe.feature.photo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sapphire.hawe.core.model.MissionTemplates

/** 마이페이지 "사진 설정"의 얼굴 가리기 선택. 이 기기에만 저장하고 서버로 보내지 않아요. */
enum class BlurChoice(val label: String, val detail: String) {
    AUTO("추천", "갤러리에서 고른 사진은 얼굴을 모두 가리고, 찍은 사진은 누른 얼굴만 가려요"),
    ALL("항상 모두 가리기", "찾은 얼굴을 모두 가린 채로 시작해요"),
    NONE("눌러서 가리기", "얼굴을 찾기만 하고, 누른 얼굴만 가려요"),
    OFF("사용 안 함", "얼굴을 찾지 않고 사진을 그대로 올려요"),
}

/**
 * 사진 틀과 설정으로 기본 모드를 정해요(AI_FEATURES.md §6 결정 3 기본값).
 * 갤러리 발굴 사진에는 남의 얼굴이 섞이기 쉬워서 "추천"이면 모두 가린 채로 시작해요.
 */
fun blurModeFor(template: String, choice: BlurChoice): BlurMode = when (choice) {
    BlurChoice.AUTO -> if (template == MissionTemplates.PHOTO_GALLERY) BlurMode.ALL else BlurMode.NONE
    BlurChoice.ALL -> BlurMode.ALL
    BlurChoice.NONE -> BlurMode.NONE
    BlurChoice.OFF -> BlurMode.OFF
}

object PhotoPrefs {
    private const val KEY_BLUR_CHOICE = "blurChoice"
    private var preferences: SharedPreferences? = null
    var blurChoice by mutableStateOf(BlurChoice.AUTO)
        private set

    /** 처음 쓸 때 한 번 불러와요(여러 번 불러도 괜찮아요). */
    fun init(context: Context) {
        if (preferences != null) return
        val loaded = context.applicationContext.getSharedPreferences("hawe_photo", Context.MODE_PRIVATE)
        preferences = loaded
        blurChoice = parseBlurChoice(loaded.getString(KEY_BLUR_CHOICE, null))
    }

    fun changeBlurChoice(value: BlurChoice) {
        blurChoice = value
        preferences?.edit()?.putString(KEY_BLUR_CHOICE, value.name)?.apply()
    }
}

/** 저장값이 없거나 옛 이름이면 "추천" */
internal fun parseBlurChoice(value: String?): BlurChoice = BlurChoice.entries.firstOrNull { it.name == value } ?: BlurChoice.AUTO
