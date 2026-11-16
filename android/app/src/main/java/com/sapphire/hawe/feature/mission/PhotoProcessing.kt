package com.sapphire.hawe.feature.mission

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

fun centerCropRect(width: Int, height: Int): CropRect {
    if (width <= 0 || height <= 0) return CropRect(0, 0, 0, 0)
    val cropWidth = minOf(width, (height.toLong() * 3 / 4).toInt().coerceAtLeast(1))
    val cropHeight = minOf(height, (width.toLong() * 4 / 3).toInt().coerceAtLeast(1))
    return CropRect((width - cropWidth) / 2, (height - cropHeight) / 2, cropWidth, cropHeight)
}

fun sampleSizeFor(width: Int, height: Int, maxSide: Int = 2048): Int {
    if (width <= 0 || height <= 0 || maxSide <= 0) return 1
    var sample = 1
    while (maxOf(width.toLong(), height.toLong()) / sample > maxSide) sample *= 2
    return sample
}

fun loadCroppedPhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    val upright = loadUprightPhoto(context, uri) ?: return null
    val rect = centerCropRect(upright.width, upright.height)
    Bitmap.createBitmap(upright, rect.x, rect.y, rect.width, rect.height).also {
        if (it !== upright) upright.recycle()
    }
}.getOrNull()

/** 사진을 긴 변 [maxSide] 이하로 줄여 불러오고 EXIF 방향대로 세워요(자르지 않음). */
private fun loadUprightPhoto(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? {
    val resolver = context.contentResolver
    // PNG 등 EXIF 를 못 읽는 형식은 방향 정보가 없는 걸로 봐요
    val orientation = resolver.openInputStream(uri)?.use { stream ->
        runCatching { ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide) }
    val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    return uprightBitmap(original, orientation)
}

private fun uprightBitmap(original: Bitmap, orientation: Int): Bitmap {
    val angle = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val flip = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL || orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
        orientation == ExifInterface.ORIENTATION_TRANSPOSE || orientation == ExifInterface.ORIENTATION_TRANSVERSE
    val matrix = Matrix().apply {
        if (flip) postScale(if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) 1f else -1f,
            if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) -1f else 1f)
        postRotate(when (orientation) {
            ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            else -> angle
        })
    }
    return if (angle != 0f || flip) Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also {
        if (it !== original) original.recycle()
    } else original
}

/** 고른 파일 형식이 PNG 면 PNG 로(투명한 부분 유지), 아니면 JPEG 로 다시 저장해요. 서버는 jpg·png·webp 만 받아요. */
internal fun reencodeAsPng(mimeType: String?): Boolean = mimeType.equals("image/png", ignoreCase = true)

/**
 * 고른 사진을 **새로 인코딩한** 바이트와 확장자(댓글 첨부용). 원본 파일을 그대로 올리면 EXIF(기기 모델·촬영 시각,
 * 기기에 따라 위치)가 따라가서, 방향만 바로잡아 다시 저장해요. 긴 변은 [maxSide] 이하로 줄여요.
 * HEIC 처럼 서버가 받지 않는 형식도 JPEG 로 바뀌어 올라가요. 읽거나 변환하지 못하면 null.
 */
fun reencodedPhotoBytes(context: Context, uri: Uri, maxSide: Int = 2048): Pair<ByteArray, String>? = runCatching {
    val png = reencodeAsPng(context.contentResolver.getType(uri))
    val upright = loadUprightPhoto(context, uri, maxSide) ?: return null
    try {
        ByteArrayOutputStream().use { output ->
            val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            if (!upright.compress(format, if (png) 100 else 90, output)) return null
            output.toByteArray() to if (png) "png" else "jpg"
        }
    } finally { upright.recycle() }
}.getOrNull()

fun photoJpegBytes(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { output ->
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
    output.toByteArray()
}
