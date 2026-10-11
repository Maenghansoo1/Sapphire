package com.sapphire.hawe.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.designsystem.assets.*
import com.sapphire.hawe.designsystem.brand.*
import com.sapphire.hawe.designsystem.components.*
import com.sapphire.hawe.designsystem.theme.HaweTheme
import com.sapphire.hawe.designsystem.tokens.*

@Composable
fun ComponentGalleryScreen() {
    var relation by remember { mutableStateOf(Relationship.FRIEND) }
    var email by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("하늘") }
    var notify by remember { mutableStateOf(true) }
    var agreed by remember { mutableStateOf(false) }
    var avatar by remember { mutableStateOf<AvatarContent>(AvatarContent.Character(ProfileCharacter.MONGSIL)) }
    var showAlert by remember { mutableStateOf(false) }
    Column(Modifier.background(HColors.bg).verticalScroll(rememberScrollState())
        .padding(horizontal = HLayout.screenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(36.dp)) {
        HLogoLockup()
        Section("색상 토큰") {
            listOf("bg" to HColors.bg, "surface" to HColors.surface, "surfaceStrong" to HColors.surfaceStrong,
                "line" to HColors.line, "border" to HColors.border, "ink" to HColors.ink,
                "textSecondary" to HColors.textSecondary, "textTertiary" to HColors.textTertiary,
                "accent" to HColors.accent, "accentPressed" to HColors.accentPressed,
                "accentSubtle" to HColors.accentSubtle, "danger" to HColors.danger,
                "disabled" to HColors.disabled, "controlBorder" to HColors.controlBorder,
                "controlOff" to HColors.controlOff).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (name, color) ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Spacer(Modifier.fillMaxWidth().height(44.dp).background(color, RoundedCornerShape(12.dp))
                                .border(1.dp, HColors.line, RoundedCornerShape(12.dp)))
                            Text(name, style = HType.caption12, color = HColors.textSecondary)
                        }
                    }
                }
            }
        }
        Section("타이포그래피") {
            Text("화면 제목 26 Bold", style = HType.screenTitle)
            Text("내비 제목 17 Bold", style = HType.navTitle)
            Text("섹션 제목 16 Bold", style = HType.sectionTitle)
            Text("입력 라벨 14 SemiBold", style = HType.fieldLabel)
            Text("본문 15 Regular — 매일 하나의 미션, 우리만의 기록", style = HType.body15)
            Text("보조 13 Regular", style = HType.caption13)
            Text("캡션 12 Regular", style = HType.caption12)
        }
        Section("로고") { Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HLogoSymbol(88.dp); HLogoSymbol(44.dp); HLogoSymbol(30.dp); HWordmark(26)
        } }
        Section("캐릭터 7종") {
            ProfileCharacter.entries.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { character ->
                    Column { HAvatar(AvatarContent.Character(character), size = 66.dp)
                        Text(character.displayName, style = HType.caption12) }
                } }
            }
        }
        Section("아이콘") {
            HIcon.entries.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { row.forEach { HIconView(it) } }
            }
        }
        Section("관계 장식") { Relationship.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { Column { RelationshipDecoration(it, 28.dp); Text(it.displayName, style = HType.caption12) } }
            }
        } }
        Section("HButton") {
            HButton("새 그룹 만들기") {}
            HButton("초대 코드로 참여하기", kind = HButtonKind.SECONDARY) {}
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HButton("취소", Modifier.weight(1f), HButtonKind.GRAY, 50.dp) {}
                HButton("로그아웃", Modifier.weight(1f), HButtonKind.DANGER, 50.dp) {}
            }
            HButton("탈퇴하기", kind = HButtonKind.DISABLED) {}
        }
        Section("HChip") { Relationship.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach {
                HChip(it.displayName, relation == it) { relation = it }
            } }
        } }
        Section("HNavBar") {
            HNavBar("그룹 정보", HNavLeading.BACK)
            HNavBar(leading = HNavLeading.BACK, trailing = { HNavStep("1 / 2") })
            HNavBar("알림 설정", HNavLeading.CLOSE)
        }
        Section("HAvatar") { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(24, 30, 36, 44).forEach { HAvatar(AvatarContent.Character(ProfileCharacter.MONGSIL), size = it.dp) }
        } }
        Section("HTextField") {
            HTextField(email, { email = it }, "example@email.com", label = "이메일")
            HTextField(nickname, { nickname = it }, "닉네임", label = "닉네임", maxLength = 10,
                helper = "캐릭터와 이름은 나중에 바꿀 수 있어요")
            HTextField("", {}, "비밀번호", label = "비밀번호", error = "8자 이상, 영문과 숫자를 함께 써 주세요")
        }
        Section("HToggle") { HToggle(notify, { notify = it }) }
        Section("HCheckbox") { HCheckbox(agreed, { agreed = it }) { Text("전체 동의", style = HType.plex(15, FontWeight.Bold)) } }
        Section("HListRow") { HListRow("주제 도착 시간", value = "오전 9:00") {}; HListRow("알림", value = "켜짐") {} }
        Section("MemberRow") { Relationship.entries.forEach { relationship ->
            MemberRow(ProfileCharacter.entries.take(if (relationship == Relationship.LOVER) 2 else 4)
                .map { AvatarContent.Character(it) }, relationship)
        } }
        Section("CharacterPicker") { CharacterPicker(avatar, { avatar = it }) }
        Section("HAlert") { HButton("알림창 보기", kind = HButtonKind.GRAY) { showAlert = true } }
        Section("HToast") { HToast("저장했어요") }
    }
    if (showAlert) HAlert("로그아웃할까요?", "다시 로그인하면 그룹과 기록은\n그대로 남아 있어요.",
        "로그아웃", onCancel = { showAlert = false }, onConfirm = { showAlert = false })
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = HType.sectionTitle, color = HColors.ink)
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun GalleryPreview() { HaweTheme { ComponentGalleryScreen() } }
