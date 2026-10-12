package com.sapphire.hawe.designsystem.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.assets.ProfileCharacter
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType
import java.io.File
import java.util.UUID

@Composable
fun CharacterPicker(selection: AvatarContent, onSelect: (AvatarContent) -> Unit,
    cellSize: Dp = 66.dp, characterSize: Dp = 50.dp, columnSpacing: Dp = 26.dp, rowSpacing: Dp = 12.dp,
    showPhotoCell: Boolean = true) {
    val context = LocalContext.current
    var lastPhoto by remember { mutableStateOf<AvatarContent.Photo?>(selection as? AvatarContent.Photo) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val photo = runCatching {
                val folder = File(context.cacheDir, "hawe-media").apply { mkdirs() }
                val file = File(folder, "${UUID.randomUUID()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    ?: return@runCatching null
                AvatarContent.Photo(Uri.fromFile(file))
            }.getOrNull()
            if (photo != null) { lastPhoto = photo; onSelect(photo) }
        }
    }
    val choices = ProfileCharacter.entries.map { AvatarContent.Character(it) }
    BoxWithConstraints {
    val spacing = minOf(columnSpacing, ((maxWidth - cellSize * 4) / 3).coerceAtLeast(0.dp))
    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
        (choices + if (showPhotoCell) listOf(lastPhoto ?: AvatarContent.Character(ProfileCharacter.DEFAULT)) else emptyList())
            .chunked(4).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEachIndexed { columnIndex, choice ->
                    val photoCell = showPhotoCell && rowIndex == 1 && columnIndex == 3
                    val selected = if (photoCell) selection.isPhoto else selection == choice
                    Column(Modifier.weight(1f).clickable {
                        if (photoCell) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        else onSelect(choice)
                    }, horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val shape = CircleShape
                        Box(Modifier.size(cellSize).clip(shape)
                            .background(if (selected) HColors.bg else HColors.surface)
                            .then(if (selected) Modifier.border(2.5.dp, HColors.ink, shape) else Modifier)
                            .then(if (photoCell && lastPhoto == null && !selected) Modifier.drawBehind {
                                drawCircle(HColors.controlBorder, radius = size.minDimension / 2 - 1.dp.toPx(),
                                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                            } else Modifier),
                            contentAlignment = Alignment.Center) {
                            if (photoCell) {
                                if (lastPhoto != null) HAvatar(lastPhoto!!, size = cellSize)
                                else HIconView(HIcon.IMAGE, size = 22.dp)
                            } else Image(painterResource((choice as AvatarContent.Character).character.drawable), null,
                                Modifier.size(characterSize))
                        }
                        Text(if (photoCell) "내 사진" else (choice as AvatarContent.Character).character.displayName,
                            style = HType.plex(12, if (selected) FontWeight.Bold else FontWeight.Medium),
                            color = if (selected) HColors.ink else HColors.textSecondary)
                    }
                }
            }
        }
    }
    }
}
