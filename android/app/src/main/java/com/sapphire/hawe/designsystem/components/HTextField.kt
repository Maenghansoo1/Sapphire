package com.sapphire.hawe.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sapphire.hawe.designsystem.tokens.HColors
import com.sapphire.hawe.designsystem.tokens.HRadius
import com.sapphire.hawe.designsystem.tokens.HType

@Composable
fun HTextField(value: String, onValueChange: (String) -> Unit, placeholder: String,
    modifier: Modifier = Modifier, label: String? = null, isSecure: Boolean = false,
    helper: String? = null, maxLength: Int? = null, error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text, textSize: Int = 16,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    autoCorrectEnabled: Boolean = true, imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) Text(label, style = HType.fieldLabel, color = HColors.textSecondary)
        val shape = RoundedCornerShape(HRadius.input)
        BasicTextField(value, { onValueChange(if (maxLength == null) it else it.take(maxLength)) },
            modifier = Modifier.fillMaxWidth().height(54.dp).background(HColors.bg, shape)
                .border(1.dp, if (error == null) HColors.border else HColors.danger, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            textStyle = HType.plex(textSize).copy(color = HColors.ink), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (isSecure) KeyboardType.Password else keyboardType,
                capitalization = capitalization, autoCorrectEnabled = autoCorrectEnabled, imeAction = imeAction),
            keyboardActions = if (onImeAction == null) KeyboardActions.Default
                else KeyboardActions(onAny = { onImeAction() }),
            visualTransformation = if (isSecure) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = HType.plex(textSize), color = HColors.textTertiary)
                    inner()
                }
            })
        if (error != null) Text(error, style = HType.caption13, color = HColors.danger)
        else if (helper != null || maxLength != null) Row {
            if (helper != null) Text(helper, style = HType.caption13, color = HColors.textSecondary)
            Spacer(Modifier.weight(1f))
            if (maxLength != null) Text("${value.length} / $maxLength", style = HType.caption13, color = HColors.textSecondary)
        }
    }
}
