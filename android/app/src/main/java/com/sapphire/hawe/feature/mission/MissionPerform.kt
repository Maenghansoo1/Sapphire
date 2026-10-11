package com.sapphire.hawe.feature.mission

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.HAlert
import com.sapphire.hawe.designsystem.HColors
import com.sapphire.hawe.designsystem.HIcon
import com.sapphire.hawe.designsystem.HIconView
import com.sapphire.hawe.designsystem.HLayout
import com.sapphire.hawe.designsystem.HNavBar
import com.sapphire.hawe.designsystem.HScreen
import com.sapphire.hawe.designsystem.NavLeading
import com.sapphire.hawe.designsystem.plainClickable
import com.sapphire.hawe.designsystem.plex
import com.sapphire.hawe.model.MissionType
import com.sapphire.hawe.model.SubmissionContent
import com.sapphire.hawe.model.newId
import java.io.File
import java.io.OutputStream

/**
 * 미션 수행 화면 (06 그림 · 07 사진 · 08 글 · 09 음성) — 05 위에 전체 화면으로 띄워요.
 * 제출 후에는 수정·삭제할 수 없어요 (CLAUDE.md §5-2).
 * TODO: 서버 연결 시 Storage 업로드(submissions/{group}/{mission}/{user}.png|jpg|m4a) 후 제출 저장
 */
@Composable
fun MissionPerformScreen(type: MissionType, topic: String, onClose: () -> Unit, onSubmit: (SubmissionContent) -> Unit) {
    // 아래 05 화면으로 터치가 넘어가지 않게 막아요.
    Box(Modifier.fillMaxSize().background(HColors.bg).plainClickable {}) {
        when (type) {
            MissionType.Draw -> DrawMissionScreen(topic, onClose, onSubmit)
            MissionType.Photo -> PhotoMissionScreen(topic, onClose, onSubmit)
            MissionType.Write -> WriteMissionScreen(topic, onClose, onSubmit)
            MissionType.Voice -> VoiceMissionScreen(topic, onClose, onSubmit)
        }
    }
}

/**
 * 06~09 공통 틀 — 닫기 헤더 + 오른쪽 도구. 쓰던 내용이 있으면 닫기 전에 물어봐요.
 * [overlay] 는 화면 전체 위에 그려요 (권한 알림창 등).
 */
@Composable
fun MissionScaffold(
    hasDraft: Boolean,
    onClose: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmsClose by remember { mutableStateOf(false) }
    val close = { if (hasDraft) confirmsClose = true else onClose() }
    BackHandler { if (confirmsClose) confirmsClose = false else close() }

    Box(Modifier.fillMaxSize()) {
        HScreen {
            HNavBar(modifier = Modifier.padding(horizontal = HLayout.screenPaddingCompact), leading = NavLeading.Close(close), trailing = trailing)
            content()
        }
        overlay()
        if (confirmsClose) {
            HAlert(
                "그만둘까요?",
                "지금까지 한 내용은 저장되지 않아요.",
                confirmTitle = "그만두기",
                cancelTitle = "계속하기",
                onCancel = { confirmsClose = false },
                onConfirm = {
                    confirmsClose = false
                    onClose()
                },
            )
        }
    }
}

/** 06~09 공통 위쪽 — "오늘의 ○○" 13 SemiBold + 주제 20 Bold (위 4) */
@Composable
fun MissionTitle(type: MissionType, topic: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("오늘의 ${type.displayName}", style = plex(13, FontWeight.SemiBold), color = HColors.textSecondary)
        Text(topic, style = plex(20, FontWeight.Bold), color = HColors.ink)
    }
}

/**
 * 헤더 오른쪽 아이콘 줄 — 아이콘 22 사이 16, 마지막 아이콘이 화면 여백에 딱 붙어요.
 * 누르는 영역은 아이콘마다 44 라서 서로 6 씩 겹쳐 두고 오른쪽으로 11 내밀어요.
 */
@Composable
fun HeaderIconRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.offset(x = 11.dp), horizontalArrangement = Arrangement.spacedBy((-6).dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun HeaderIconButton(icon: HIcon, size: Dp = 22.dp, tint: Color = HColors.ink, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(HLayout.minTouch).plainClickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        HIconView(icon, size, tint)
    }
}

/** 꺼져 있으면 흐리게 (Figma 에 비활성 모양이 없어서 iOS 와 같은 40%) */
fun Modifier.dimmed(enabled: Boolean): Modifier = alpha(if (enabled) 1f else 0.4f)

/** 올릴 파일을 앱 안 저장소에 만들어요 (서버 연결 전까지 결과 화면이 이 파일을 읽어요). */
fun newSubmissionFile(context: Context, extension: String): File =
    File(context.filesDir, "submissions").apply { mkdirs() }.resolve("${newId()}.$extension")

fun writeSubmissionFile(context: Context, extension: String, write: (OutputStream) -> Unit): Uri {
    val file = newSubmissionFile(context, extension)
    file.outputStream().use(write)
    return Uri.fromFile(file)
}
