package com.sapphire.hawe.core.model

import com.sapphire.hawe.core.josa
import com.sapphire.hawe.designsystem.assets.HIcon

enum class MissionType(val value: String, val displayName: String, val icon: HIcon, val actionTitle: String) {
    DRAW("draw", "그림", HIcon.PENCIL, "그림 그리기"),
    PHOTO("photo", "사진", HIcon.CAMERA, "사진 찍기"),
    WRITE("write", "글", HIcon.TEXT, "글 쓰기");
    /** "그림이", "글이"처럼 받침에 맞춘 이름 + 이/가 */
    val subjectForm: String get() = displayName + josa(displayName, "이", "가")
    /** "그림을", "글을"처럼 받침에 맞춘 이름 + 을/를 */
    val objectForm: String get() = displayName + josa(displayName, "을", "를")
}
