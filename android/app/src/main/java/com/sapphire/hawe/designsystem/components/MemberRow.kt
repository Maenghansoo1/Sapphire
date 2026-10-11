package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.core.model.AvatarContent
import com.sapphire.hawe.designsystem.assets.Relationship
import com.sapphire.hawe.designsystem.assets.RelationshipDecoration
import kotlin.math.roundToInt

@Composable
fun MemberRow(members: List<AvatarContent>, relationship: Relationship, avatarSize: Dp = 34.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
        members.forEachIndexed { index, member ->
            if (index > 0) Box(Modifier.zIndex(1f)) {
                RelationshipDecoration(relationship, (avatarSize.value * 0.59f).roundToInt().dp)
            }
            HAvatar(member, size = avatarSize, ringed = true)
        }
    }
}
