package com.sapphire.hawe.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.tokens.*
import com.sapphire.hawe.feature.photo.BlurChoice
import com.sapphire.hawe.feature.photo.PhotoPrefs

/** 사진 미션의 얼굴 가리기 기본값(TODO_2/C3). 이 기기에만 저장해요. 임시 UI: Figma 확정 시 교체. */
@Composable
fun PhotoSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { PhotoPrefs.init(context) }
    val current = PhotoPrefs.blurChoice
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("사진 설정", leading = HNavLeading.BACK, onLeading = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text("얼굴 가리기", Modifier.padding(top = 8.dp, bottom = 4.dp),
                style = HType.plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
            BlurChoice.entries.forEach { choice ->
                Column {
                    Row(Modifier.fillMaxWidth()
                        .selectable(selected = choice == current, role = Role.RadioButton) { PhotoPrefs.changeBlurChoice(choice) }
                        .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(choice.label, style = HType.plex(15, if (choice == current) FontWeight.SemiBold else FontWeight.Normal),
                                color = HColors.ink)
                            Text(choice.detail, style = HType.caption12, color = HColors.textSecondary)
                        }
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (choice == current) HIconView(HIcon.CHECK, size = 20.dp, color = HColors.accentPressed)
                        }
                    }
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(HColors.line))
                }
            }
            Text("사진 미션에서 사진을 고르거나 찍으면 휴대폰 안에서 얼굴을 찾아 모자이크해요. 사진은 휴대폰 밖으로 나가지 않고, " +
                "가린 사진만 올라가요. 자동으로 못 찾는 얼굴이 있을 수 있으니 올리기 전에 꼭 확인해 주세요.",
                Modifier.padding(top = 16.dp), style = HType.caption12, color = HColors.textSecondary)
        }
    }
}
