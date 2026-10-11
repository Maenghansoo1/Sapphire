package com.sapphire.hawe.designsystem.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.sapphire.hawe.R
import com.sapphire.hawe.designsystem.tokens.HColors

enum class Relationship(val value: String, val displayName: String, val decorPhrase: String, val decorColor: Color) {
    LOVER("lover", "연인", "하트가", Color(0xFFE5484D)),
    FRIEND("friend", "친구", "반짝이가", Color(0xFFF0B429)),
    FAMILY("family", "가족", "작은 집이", Color(0xFFD98B63)),
    RIVAL("rival", "웬수", "번개가", Color(0xFFE8590C)),
    SOSO("soso", "그저그런사이", "말줄임표가", Color(0xFF8E8E93));
    val maxMembers: Int get() = if (this == LOVER) 2 else 6
}

@Composable
fun RelationshipDecoration(relationship: Relationship, diameter: Dp = 20.dp) {
    Box(Modifier.size(diameter).clip(CircleShape).background(HColors.bg), contentAlignment = Alignment.Center) {
        val drawable = when (relationship) {
            Relationship.LOVER -> R.drawable.decor_lover
            Relationship.FRIEND -> R.drawable.decor_friend
            Relationship.RIVAL -> R.drawable.decor_enemy
            Relationship.FAMILY -> R.drawable.decor_family
            Relationship.SOSO -> R.drawable.decor_soso
        }
        Icon(painterResource(drawable), contentDescription = null, modifier = Modifier.size(diameter * 0.7f),
            tint = if (relationship == Relationship.FAMILY || relationship == Relationship.SOSO) relationship.decorColor else Color.Unspecified)
    }
}
