package com.sapphire.hawe.designsystem.tokens

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.sapphire.hawe.R

object HType {
    val PlexFamily = FontFamily(
        Font(R.font.ibm_plex_sans_kr_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_kr_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_sans_kr_semibold, FontWeight.SemiBold),
        Font(R.font.ibm_plex_sans_kr_bold, FontWeight.Bold),
    )
    val FredokaFamily = FontFamily(Font(R.font.fredoka_semibold, FontWeight.SemiBold))
    fun plex(size: Int, weight: FontWeight = FontWeight.Normal, lineHeightMultiple: Float = 1.3f) = TextStyle(
        fontFamily = PlexFamily, fontWeight = weight, fontSize = size.sp,
        lineHeight = (size * lineHeightMultiple).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    fun wordmark(size: Int) = TextStyle(fontFamily = FredokaFamily, fontWeight = FontWeight.SemiBold, fontSize = size.sp)
    val screenTitle = plex(26, FontWeight.Bold)
    val navTitle = plex(17, FontWeight.Bold)
    val sectionTitle = plex(16, FontWeight.Bold)
    val fieldLabel = plex(14, FontWeight.SemiBold)
    val body15 = plex(15)
    val caption13 = plex(13)
    val caption12 = plex(12)
}
