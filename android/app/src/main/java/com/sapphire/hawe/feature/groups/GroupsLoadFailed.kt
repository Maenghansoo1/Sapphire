package com.sapphire.hawe.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sapphire.hawe.designsystem.components.HButton
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun GroupsLoadFailed(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().background(HColors.bg),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("그룹을 불러오지 못했어요", style = HType.screenTitle, color = HColors.ink)
        Text("네트워크 연결을 확인해 주세요", style = HType.body15, color = HColors.textSecondary)
        HButton("다시 시도", onClick = onRetry)
    }
}
