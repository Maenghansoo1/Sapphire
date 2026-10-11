package com.sapphire.hawe.core.model

import com.sapphire.hawe.designsystem.assets.HIcon

enum class MissionType(val value: String, val displayName: String, val icon: HIcon, val actionTitle: String) {
    DRAW("draw", "그림", HIcon.PENCIL, "그림 그리기"),
    PHOTO("photo", "사진", HIcon.CAMERA, "사진 찍기"),
    WRITE("write", "글", HIcon.TEXT, "글 쓰기"),
    VOICE("voice", "음성", HIcon.MIC, "음성 녹음");
    val subjectForm: String get() = "${displayName}이"
}
