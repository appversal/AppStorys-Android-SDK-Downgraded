package com.appversal.appstorys.ui.common_components

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.utils.personalizeText
import com.appversal.appstorys.utils.toColor
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.launch
import com.appversal.appstorys.utils.FontCache

@Composable
fun CommonText(
    modifier: Modifier = Modifier,
    text: String,
    styling: TextStyling,
    lineHeight: Float? = null,
    letterSpacing: Float? = null,
    maxLines: Int? = null
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State to hold the loaded font family
    var fontFamily by remember { mutableStateOf<FontFamily?>(null) }
    var isLoadingFont by remember { mutableStateOf(false) }

    val decoration = styling.fontDecoration.orEmpty()

    val fontWeight =
        if (decoration.contains("bold")) FontWeight.Bold else if (decoration.contains("semibold")) FontWeight.SemiBold else if (decoration.contains(
                "medium"
            )
        ) FontWeight.Medium else FontWeight.Normal
    val fontStyle =
        if (decoration.contains("italic")) FontStyle.Italic else FontStyle.Normal
    val textDecoration =
        if (decoration.contains("underline")) TextDecoration.Underline else null

    LaunchedEffect(styling.fontFamily) {
        if (!styling.fontFamily.isNullOrBlank() && isUrl(styling.fontFamily)) {
            isLoadingFont = true
            scope.launch {
                try {
                    val loadedFont = FontCache.loadFont(
                        context = context,
                        fontUrl = styling.fontFamily,
                        weight = fontWeight,
                        style = fontStyle
                    )
                    fontFamily = loadedFont ?: getDefaultFontFamily()
                } catch (e: Exception) {
                    // Fallback to default font on error
                    fontFamily = getDefaultFontFamily()
                } finally {
                    isLoadingFont = false
                }
            }
        } else if (!styling.fontFamily.isNullOrBlank()) {
            // Handle system font families
            fontFamily = getSystemFontFamily(styling.fontFamily)
        } else {
            // Use default font
            fontFamily = getDefaultFontFamily()
        }
    }

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
            fontFamily = fontFamily ?: getDefaultFontFamily(),
            textAlign = parseTextAlign(styling.textAlign),
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textDecoration = textDecoration
        )
    )
}

/**
 * Check if a string is a URL
 */
private fun isUrl(str: String): Boolean {
    return str.startsWith("http://", ignoreCase = true) ||
            str.startsWith("https://", ignoreCase = true)
}

/**
 * Get system font family by name
 */
private fun getSystemFontFamily(fontName: String): FontFamily {
    return when (fontName.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace", "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "sans-serif", "sans" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
}

/**
 * Get default font family
 */
private fun getDefaultFontFamily(): FontFamily {
    return FontFamily.SansSerif
}