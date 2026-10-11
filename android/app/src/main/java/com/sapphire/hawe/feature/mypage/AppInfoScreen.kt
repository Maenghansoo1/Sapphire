package com.sapphire.hawe.feature.mypage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.assets.HIcon
import com.sapphire.hawe.designsystem.assets.HIconView
import com.sapphire.hawe.designsystem.brand.HLogoSymbol
import com.sapphire.hawe.designsystem.brand.HWordmark
import com.sapphire.hawe.designsystem.components.HListRow
import com.sapphire.hawe.designsystem.components.HNavBar
import com.sapphire.hawe.designsystem.components.HNavLeading
import com.sapphire.hawe.designsystem.layout.ctaBottomPadding
import com.sapphire.hawe.designsystem.tokens.*

private const val contactEmail = "devleeekh@gmail.com"

private fun launchUri(context: Context, uri: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

@Composable
fun AppInfoScreen(onBack: () -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val version = rememberAppVersionName()
    val latestVersion = version // TODO: 서버 연결 시 최신 버전을 읽어요.
    Column(Modifier.fillMaxSize().background(HColors.bg).padding(horizontal = HLayout.screenPaddingCompact)) {
        HNavBar("앱 정보", leading = HNavLeading.BACK, onLeading = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(top = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp).background(HColors.bg, RoundedCornerShape(HRadius.card))
                    .border(1.dp, HColors.disabled, RoundedCornerShape(HRadius.card)),
                    contentAlignment = Alignment.Center) { HLogoSymbol(62.dp) }
                HWordmark(26)
                Text("Hi + We, 매일 건네는 우리의 인사", style = HType.caption13, color = HColors.textSecondary)
                Text("버전 $version", style = HType.plex(14), color = HColors.textSecondary)
            }
            Row(Modifier.fillMaxWidth().padding(top = 22.dp)
                .background(HColors.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(AppVersionInfo.versionStatusTitle(version, latestVersion),
                        style = HType.plex(15, FontWeight.SemiBold), color = HColors.ink)
                    Text("최신 버전 $latestVersion", style = HType.caption12, color = HColors.textSecondary)
                }
                HIconView(HIcon.CHECK, size = 22.dp)
            }
            Column(Modifier.padding(top = 22.dp)) {
                HListRow("문의하기", value = contactEmail, height = 54.dp) {
                    launchUri(context, mailtoUri(contactEmail))
                }
                HListRow("앱 평가하기", value = "Google Play", height = 54.dp) {
                    if (!launchUri(context, playStoreMarketUri(context.packageName)))
                        launchUri(context, playStoreWebUri(context.packageName))
                }
                HListRow("오픈소스 라이선스", height = 54.dp, onClick = onLicenses)
            }
        }
        Text(AppVersionInfo.footer, Modifier.fillMaxWidth().padding(top = 12.dp,
            bottom = ctaBottomPadding(32.dp)), style = HType.plex(12, lineHeightMultiple = 1.6f),
            color = HColors.textSecondary, textAlign = TextAlign.Center)
    }
}
