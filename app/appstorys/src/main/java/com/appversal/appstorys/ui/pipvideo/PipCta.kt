package com.appversal.appstorys.ui.pipvideo

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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

    // Debug logging
    Log.d("PipCta", "=== PIP CTA Button Styling ===")
    Log.d("PipCta", "Button text: $buttonText")
    Log.d("PipCta", "Link: $link")
    Log.d("PipCta", "CTA structured data: $cta")
    Log.d("PipCta", "Flat styling: $pipStyling")

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
        val bg = cta?.container?.backgroundColor ?: pipStyling?.ctaButtonBackgroundColor ?: "#F7921C"
        Color(bg.toColorInt())
    }.getOrNull() ?: Color(0xFFF7921C)

    val textColor = runCatching {
        val tc = cta?.text?.color ?: pipStyling?.ctaButtonTextColor ?: "#FFFFFF"
        Color(tc.toColorInt())
    }.getOrNull() ?: Color.White

    // Border
    val borderColor = runCatching {
        val c = cta?.container?.borderColor ?: "#FE6B35"
        Color(c.toColorInt())
    }.getOrNull() ?: Color(0xFFFE6B35)

    val borderWidth = runCatching {
        val w = cta?.container?.borderWidth
        if (!w.isNullOrBlank()) w.toFloat().dp else 0.dp
    }.getOrNull() ?: 0.dp

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

    val shape = RoundedCornerShape(
        topStart = cornerTopLeft,
        topEnd = cornerTopRight,
        bottomEnd = cornerBottomRight,
        bottomStart = cornerBottomLeft
    )

    // Width handling: structured cta.container.ctaFullWidth / ctaWidth or flat fields
    val isFullWidth = cta?.container?.ctaFullWidth == true || pipStyling?.ctaFullWidth == true
    val fixedWidth = if (!isFullWidth) {
        cta?.container?.ctaWidth?.dp ?: pipStyling?.ctaWidth?.toIntOrNull()?.dp
    } else null

    // Alignment
    val alignmentStr = cta?.container?.alignment ?: "center"
    val contentAlignment = when (alignmentStr.lowercase()) {
        "left" -> Alignment.CenterStart
        "right" -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    val textAlign = when (alignmentStr.lowercase()) {
        "left" -> TextAlign.Start
        "right" -> TextAlign.End
        else -> TextAlign.Center
    }

    // Font
    val fontFamilyName = cta?.text?.fontFamily ?: pipStyling?.fontFamily
    val fontFamily = when (fontFamilyName?.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif", "sansserif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default // Default for Helvetica, Arial, or any unrecognized font
    }
    val fontSizeSp = cta?.text?.fontSize?.sp ?: pipStyling?.fontSize?.toIntOrNull()?.sp ?: 16.sp

    // Font weight, style, and decoration (support from backend if available)
    val fontWeight = mapFontWeight(cta?.text?.fontWeight)
    val fontStyle = mapFontStyle(cta?.text?.fontStyle)
    val textDecoration = if (
        cta?.text?.textDecoration?.any { it.equals("underline", true) } == true ||
        pipStyling?.fontDecoration?.any { it.equals("underline", true) } == true
    ) {
        TextDecoration.Underline
    } else null

    // Debug logging for text styling
    Log.d("PipCta", "Font family: $fontFamilyName -> $fontFamily")
    Log.d("PipCta", "Font size: $fontSizeSp")
    Log.d("PipCta", "Font weight: ${cta?.text?.fontWeight} -> $fontWeight")
    Log.d("PipCta", "Font style: ${cta?.text?.fontStyle} -> $fontStyle")
    Log.d("PipCta", "Text decoration: ${pipStyling?.fontDecoration} -> $textDecoration")
    Log.d("PipCta", "Background color: $buttonColor")
    Log.d("PipCta", "Text color: $textColor")
    Log.d("PipCta", "Height: $heightDp")
    Log.d("PipCta", "Width: fullWidth=$isFullWidth, fixedWidth=$fixedWidth")
    Log.d("PipCta", "Alignment: $alignmentStr -> $contentAlignment")
    Log.d("PipCta", "Border: width=$borderWidth, color=$borderColor")
    Log.d("PipCta", "Corner radius: TL=$cornerTopLeft, TR=$cornerTopRight, BR=$cornerBottomRight, BL=$cornerBottomLeft")

    // Determine button container alignment (like ModalBackendCta)
    // When not fullWidth, the button itself needs to be aligned within the parent container
    val buttonContainerAlignment = when (alignmentStr.lowercase()) {
        "left" -> Alignment.CenterStart
        "right" -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    // Outer wrapper box for button alignment (when not full width)
    val wrapperModifier = modifier
        .padding(
            top = paddingTop,
            bottom = paddingBottom,
            start = paddingLeft,
            end = paddingRight
        )
        .then(if (isFullWidth) Modifier else Modifier.fillMaxWidth()) // Fill width only for alignment wrapper

    // Inner button box modifier
    val buttonModifier = Modifier
        .then(if (isFullWidth) Modifier.fillMaxWidth() else if (fixedWidth != null) Modifier.width(fixedWidth) else Modifier)
        .height(heightDp)
        .background(buttonColor, shape)
        .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier)
        .clickable {
            if (AppStorys.isValidUrl(link)) {
                AppStorys.openUrl(link)
            } else {
                AppStorys.navigateToScreen(link)
            }
            onButtonClick()
        }

    // Wrapper Box for alignment (similar to ModalBackendCta)
    Box(
        modifier = wrapperModifier,
        contentAlignment = buttonContainerAlignment
    ) {
        // Inner button Box
        Box(
            modifier = buttonModifier,
            contentAlignment = contentAlignment
        ) {
            Text(
                text = buttonText,
                color = textColor,
                fontSize = fontSizeSp,
                textAlign = textAlign,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    textDecoration = textDecoration
                )
            )
        }
    }
}

// Helper functions to map font weight and style (same as ModalBackendCta)
private fun mapFontWeight(value: String?): FontWeight = when (value?.lowercase()) {
    "bold", "700", "800" -> FontWeight.Bold
    "600" -> FontWeight.SemiBold
    "500" -> FontWeight.Medium
    else -> FontWeight.Normal
}

private fun mapFontStyle(value: String?): FontStyle =
    if (value?.equals("italic", true) == true)
        FontStyle.Italic
    else FontStyle.Normal

