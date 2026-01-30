package com.appversal.appstorys.ui.common_components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.utils.personalizeText
import com.appversal.appstorys.utils.toColor

@Composable
fun CommonText(
    modifier: Modifier = Modifier,
    text: String,
    styling: TextStyling,
    lineHeight: Float? = null,
    letterSpacing: Float? = null,
    maxLines: Int? = null
) {
    val decoration = styling.fontDecoration.orEmpty()

    val fontWeight =
        if (decoration.contains("bold")) FontWeight.Bold else if (decoration.contains("semibold")) FontWeight.SemiBold else if (decoration.contains("medium")) FontWeight.Medium else FontWeight.Normal
    val fontStyle =
        if (decoration.contains("italic")) FontStyle.Italic else FontStyle.Normal
    val textDecoration =
        if (decoration.contains("underline")) TextDecoration.Underline else null

    fun parseTextAlign(alignment: String?): TextAlign {
        return when (alignment?.lowercase()) {
            "left", "start" -> TextAlign.Start
            "right", "end" -> TextAlign.End
            "justify" -> TextAlign.Justify
            else -> TextAlign.Center
        }
    }

    Text(
        text = personalizeText(text),
        modifier = modifier.then(
            Modifier.padding(
                start = styling.margin?.left?.dp ?: 0.dp,
                end = styling.margin?.right?.dp ?: 0.dp,
                top = styling.margin?.top?.dp ?: 0.dp,
                bottom = styling.margin?.bottom?.dp ?: 0.dp
            )
        ),
        lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
        letterSpacing = letterSpacing?.sp ?: TextUnit.Unspecified,
        maxLines = maxLines ?: Int.MAX_VALUE,
        style = TextStyle(
            color = styling.color?.toColor(Color.Black) ?: Color.Black,
            fontSize = styling.fontSize?.sp ?: TextStyle.Default.fontSize,
//            fontFamily = styling.fontFamily,
            textAlign = parseTextAlign(styling.textAlign),
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration
        )
    )
}