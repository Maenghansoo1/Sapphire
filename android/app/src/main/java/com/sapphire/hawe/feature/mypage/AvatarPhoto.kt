package com.sapphire.hawe.feature.mypage

import android.content.Context
import android.net.Uri
import com.sapphire.hawe.feature.mission.reencodedPhotoBytes
import java.io.IOException

/**
 * 프로필 사진으로 올릴 바이트. 크기와 상관없이 항상 새로 인코딩해요(방향 보정, 긴 변 ≤ 2048).
 * 원본 파일을 그대로 올리면 EXIF(기기 모델·촬영 시각, 기기에 따라 위치)가 따라가요. 전에는 8MB 이하면 그대로 올렸어요.
 */
fun avatarUploadBytes(context: Context, uri: Uri): ByteArray =
    reencodedPhotoBytes(context, uri)?.first ?: throw IOException("사진을 읽을 수 없어요")
