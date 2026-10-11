package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun HAlert(title: String, message: String, confirmTitle: String, onCancel: () -> Unit, onConfirm: () -> Unit,
           cancelTitle: String = "취소", confirmKind: HButtonKind = HButtonKind.DANGER) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        val cardInteraction = remember { MutableInteractionSource() }
        Box(Modifier.fillMaxSize().background(HColors.ink.copy(alpha = 0.45f)).clickable(onClick = onCancel),
            contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)
                .background(HColors.bg, RoundedCornerShape(HRadius.largeCard))
                .clickable(interactionSource = cardInteraction, indication = null) {}
                .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = HType.plex(20, FontWeight.Bold), color = HColors.ink)
                Text(message, style = HType.plex(14, lineHeightMultiple = 1.6f), color = HColors.textSecondary,
                    textAlign = TextAlign.Center)
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HButton(cancelTitle, Modifier.weight(1f), HButtonKind.GRAY, 50.dp, onClick = onCancel)
                    HButton(confirmTitle, Modifier.weight(1f), confirmKind, 50.dp, onClick = onConfirm)
                }
            }
        }
    }
}
