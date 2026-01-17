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
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.components.CommonText
import com.appversal.appstorys.utils.personalizeText

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
        val bg = cta?.container?.backgroundColor ?: pipStyling?.ctaButtonBackgroundColor ?: "#F7921C"
        Color(bg.toColorInt())
    }.getOrNull() ?: Color(0xFFF7921C)

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
            CommonText(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = buttonText,
                styling = TextStyling(
                    color = cta?.text?.color,
                    fontSize = cta?.text?.fontSize,
                    fontFamily = "",
                    textAlign = cta?.container?.alignment,
                    fontDecoration = cta?.text?.textDecoration
                )
            )
        }
    }
}