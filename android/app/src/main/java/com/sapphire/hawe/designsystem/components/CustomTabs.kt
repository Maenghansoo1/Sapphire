package com.sapphire.hawe.designsystem.components

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

fun openInCustomTab(context: Context, url: String) {
    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
}
