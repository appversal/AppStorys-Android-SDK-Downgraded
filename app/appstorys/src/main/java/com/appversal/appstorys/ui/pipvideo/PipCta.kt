package com.appversal.appstorys.ui.pipvideo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.api.PipStyling

/**
 * Reusable CTA for PIP video. Reads either nested `pipStyling.cta` or falls back to flat `PipStyling` fields.
 */
@Composable
fun PipCta(
    buttonText: String?,
    link: String?,
    pipStyling: PipStyling?,
    modifier: Modifier = Modifier,
    applyMargins: Boolean = true,
    onButtonClick: () -> Unit
) {
    if (buttonText.isNullOrEmpty() || link.isNullOrEmpty()) return

    val cta = pipStyling?.cta

    // Margins: prefer structured cta.margin, else fall back to pipStyling margins
    val paddingLeft = if (applyMargins) {
        cta?.margin?.left?.dp ?: pipStyling?.marginLeft?.toIntOrNull()?.dp ?: 0.dp
    } else 0.dp
    val paddingRight = if (applyMargins) {
        cta?.margin?.right?.dp ?: pipStyling?.marginRight?.toIntOrNull()?.dp ?: 0.dp
    } else 0.dp
    val paddingBottom = if (applyMargins) {
        cta?.margin?.bottom?.dp ?: pipStyling?.marginBottom?.toIntOrNull()?.dp ?: 0.dp
    } else 0.dp
    val paddingTop = if (applyMargins) {
        cta?.margin?.top?.dp ?: pipStyling?.marginTop?.toIntOrNull()?.dp ?: 0.dp
    } else 0.dp

    // Colors: structured -> container.backgroundColor, text.color; fallbacks to legacy fields
    val buttonColor = runCatching {
        val bg = cta?.container?.backgroundColor ?: pipStyling?.ctaButtonBackgroundColor ?: "#000000"
        Color(bg.toColorInt())
    }.getOrNull() ?: Color.Black

    val textColor = runCatching {
        val tc = cta?.text?.color ?: pipStyling?.ctaButtonTextColor ?: "#FFFFFF"
        Color(tc.toColorInt())
    }.getOrNull() ?: Color.White

    // Border
    val borderStroke = runCatching {
        val borderColorString = cta?.container?.borderColor ?: null
        val borderWidthString = cta?.container?.borderWidth ?: null
            ?: null
        if (!borderColorString.isNullOrBlank() && !borderWidthString.isNullOrBlank()) {
            val widthFloat = borderWidthString.toFloatOrNull() ?: 0f
            if (widthFloat > 0f) {
                val parsed = Color(borderColorString.toColorInt())
                BorderStroke(widthFloat.dp, parsed)
            } else null
        } else null
    }.getOrNull()

    // Height
    val heightDp = runCatching {
        // structured container.height may be string; prefer cta.container.height, then flat ctaHeight
        val h = cta?.container?.height ?: pipStyling?.ctaHeight
        h?.toIntOrNull()?.dp ?: 48.dp
    }.getOrNull() ?: 48.dp

    // Corner radius per corner
    val cornerTopLeft = cta?.borderRadius?.topLeft?.dp
        ?: pipStyling?.cornerRadius?.toIntOrNull()?.dp ?: 0.dp
    val cornerTopRight = cta?.borderRadius?.topRight?.dp
        ?: pipStyling?.cornerRadius?.toIntOrNull()?.dp ?: 0.dp
    val cornerBottomRight = cta?.borderRadius?.bottomRight?.dp
        ?: pipStyling?.cornerRadius?.toIntOrNull()?.dp ?: 0.dp
    val cornerBottomLeft = cta?.borderRadius?.bottomLeft?.dp
        ?: pipStyling?.cornerRadius?.toIntOrNull()?.dp ?: 0.dp

    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = cornerTopLeft,
        topEnd = cornerTopRight,
        bottomEnd = cornerBottomRight,
        bottomStart = cornerBottomLeft
    )

    // Width handling: structured cta.container.ctaFullWidth / ctaWidth or flat fields
    val widthModifier = when {
        cta?.container?.ctaFullWidth == true -> Modifier.fillMaxWidth()
        cta?.container?.ctaWidth != null -> Modifier.width(cta.container.ctaWidth.dp)
        pipStyling?.ctaFullWidth == true -> Modifier.fillMaxWidth()
        pipStyling?.ctaWidth != null -> pipStyling.ctaWidth.toIntOrNull()?.let { Modifier.width(it.dp) } ?: Modifier
        else -> Modifier
    }

    val baseModifier = modifier
        .padding(
            top = paddingTop,
            bottom = paddingBottom + 10.dp,
            start = paddingLeft,
            end = paddingRight
        )
        .then(widthModifier)
        .height(heightDp)
        .let { m -> if (borderStroke != null) m.border(borderStroke, shape) else m }

    // Font
    val fontFamily = when (cta?.text?.fontFamily ?: pipStyling?.fontFamily) {
        null -> FontFamily.Default
        "Helvetica" -> FontFamily.SansSerif
        "Poppins" -> FontFamily.Default
        else -> FontFamily.Default
    }

    val fontSizeSp = cta?.text?.fontSize?.sp ?: pipStyling?.fontSize?.toIntOrNull()?.sp ?: 16.sp

    Button(
        onClick = {
            if (!link.isNullOrEmpty()) {
                if (!AppStorys.isValidUrl(link)) {
                    AppStorys.navigateToScreen(link)
                } else {
                    AppStorys.openUrl(link)
                }
            }
            onButtonClick()
        },
        modifier = baseModifier,
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
    ) {
        Text(
            fontFamily = fontFamily,
            text = buttonText,
            color = textColor,
            textAlign = TextAlign.Center,
            fontSize = fontSizeSp,
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Normal
        )
    }
}
