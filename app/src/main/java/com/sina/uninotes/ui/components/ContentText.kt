package com.sina.uninotes.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.sina.uninotes.ui.theme.UniText
import com.sina.uninotes.ui.theme.VazirmatnFamily

/**
 * User-content text that relies on Unicode bidi / ContentOrLtr.
 * Do not reverse or inject direction marks into stored text.
 */
@Composable
fun ContentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontFamily = style.fontFamily ?: VazirmatnFamily,
            textDirection = TextDirection.ContentOrLtr,
        ),
        textAlign = TextAlign.Unspecified,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun ContentTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    singleLine: Boolean = false,
    hint: String = "",
) {
    val merged = style.copy(
        fontFamily = style.fontFamily ?: VazirmatnFamily,
        textDirection = TextDirection.ContentOrLtr,
        color = style.color.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: UniText,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = merged,
        singleLine = singleLine,
        cursorBrush = SolidColor(UniText),
        decorationBox = { inner ->
            if (value.text.isEmpty() && hint.isNotEmpty()) {
                Text(
                    text = hint,
                    style = merged.copy(color = merged.color.copy(alpha = 0.45f)),
                )
            }
            inner()
        },
    )
}
