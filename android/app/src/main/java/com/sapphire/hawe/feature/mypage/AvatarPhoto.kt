package com.sapphire.hawe.feature.mypage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.sapphire.hawe.feature.mission.sampleSizeFor
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException

internal fun shouldCompressAvatar(size: Long): Boolean = size > 8L * 1024 * 1024

fun avatarUploadBytes(context: Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val original = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IOException("사진을 불러오지 못했어요")
    if (!shouldCompressAvatar(original.size.toLong())) return original
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("사진을 읽을 수 없어요")
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, 2048)
    }
    val bitmap = BitmapFactory.decodeByteArray(original, 0, original.size, options)
        ?: throw IOException("사진을 읽을 수 없어요")
    val orientation = ByteArrayInputStream(original).use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }
    val angle = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
        else -> 0f
    }
    val flip = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
        orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
        orientation == ExifInterface.ORIENTATION_TRANSVERSE
    val upright = if (angle != 0f || flip) {
        val matrix = Matrix().apply {
            if (flip) postScale(if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) 1f else -1f,
                if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) -1f else 1f)
            postRotate(angle)
        }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    } else bitmap
    return try {
        ByteArrayOutputStream().use { output ->
            if (!upright.compress(Bitmap.CompressFormat.JPEG, 90, output)) throw IOException("사진 변환에 실패했어요")
            output.toByteArray()
        }
    } finally { upright.recycle() }
}
