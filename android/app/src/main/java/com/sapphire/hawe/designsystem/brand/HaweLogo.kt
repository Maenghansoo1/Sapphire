package com.sapphire.hawe.designsystem.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.R
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun HLogoSymbol(size: Dp = 30.dp) {
    Image(painterResource(if (size > 60.dp) R.drawable.logo_symbol else R.drawable.logo_symbol_small),
        contentDescription = null, modifier = Modifier.size(size))
}

@Composable
fun HWordmark(size: Int = 24) {
    val text = buildAnnotatedString {
        append("HA")
        withStyle(SpanStyle(color = HColors.accent)) { append("'") }
        append("WE")
    }
    Text(text, style = HType.wordmark(size), color = HColors.ink)
}

@Composable
fun HLogoLockup() { Row { HLogoSymbol(); Spacer(Modifier.width(8.dp)); HWordmark() } }
