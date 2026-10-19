package com.sapphire.hawe.core.model

/** 서버 틀 코드(`MISSIONS_API.md` §1-2) 중 이 앱이 쓰는 것. */
object MissionTemplates {
    const val PHOTO_NOW = "PHOTO_NOW"
    const val PHOTO_GALLERY = "PHOTO_GALLERY"
    const val PHOTO_COLOR = "PHOTO_COLOR"
    const val TEXT_ACROSTIC = "TEXT_ACROSTIC"
    const val TEXT_FILL_BLANK = "TEXT_FILL_BLANK"
    const val TEXT_ONE_WORD = "TEXT_ONE_WORD"
    const val TEXT_REVIEW = "TEXT_REVIEW"
    const val TEXT_BALANCE = "TEXT_BALANCE"
    const val DRAW_SKETCH = "DRAW_SKETCH"
    const val DRAW_A_TO_B = "DRAW_A_TO_B"

    internal val supported = setOf(PHOTO_NOW, PHOTO_GALLERY, PHOTO_COLOR, TEXT_ACROSTIC, TEXT_FILL_BLANK,
        TEXT_ONE_WORD, TEXT_REVIEW, TEXT_BALANCE, DRAW_SKETCH, DRAW_A_TO_B)
    internal val aggregate = setOf(TEXT_BALANCE)
}

/** 이 앱에 수행 화면이 있는 틀인지. 서버가 나중에 새 틀을 켜면 05 화면은 "앱 업데이트" 안내만 보여요. */
fun supportsTemplate(template: String): Boolean = template in MissionTemplates.supported

/** 그룹 결과(`GET /missions/{id}/result`)가 있는 집계 틀(밸런스 게임)인지. */
fun isAggregateTemplate(template: String): Boolean = template in MissionTemplates.aggregate
