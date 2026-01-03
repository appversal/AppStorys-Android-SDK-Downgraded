package com.appversal.appstorys.ui.pipvideo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.appversal.appstorys.AppStorys
import com.appversal.appstorys.api.PipStyling

/**
 * Reusable CTA for PIP video. Reads either nested `pipStyling.cta` or falls back to flat `PipStyling` fields.
 * Refactored to use Box instead of Button for better styling control, similar to ModalBackendCta.
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
    val marginLeft = if (applyMargins) {
        cta?.margin?.left?.dp ?: pipStyling?.marginLeft?.toIntOrNull()?.dp ?: 0.dp
    } else 0.dp
    val marginRight = if (applyMargins) {
        cta?.margin?.right?.dp ?: pipStyling?.marginRight?.toIntOrNull()?.dp ?: 0.dp
    } else 0.dp
    val marginBottom = if (applyMargins) {
        cta?.margin?.bottom?.dp ?: pipStyling?.marginBottom?.toIntOrNull()?.dp ?: 0.dp
    } else 0.dp
    val marginTop = if (applyMargins) {
        cta?.margin?.top?.dp ?: pipStyling?.marginTop?.toIntOrNull()?.dp ?: 0.dp
    } else 0.dp

    // Colors: structured -> container.backgroundColor, text.color; fallbacks to legacy fields
    val backgroundColor = runCatching {
        val bg = cta?.container?.backgroundColor ?: pipStyling?.ctaButtonBackgroundColor ?: "#000000"
        Color(bg.toColorInt())
    }.getOrNull() ?: Color.Black

    val textColor = runCatching {
        val tc = cta?.text?.color ?: pipStyling?.ctaButtonTextColor ?: "#FFFFFF"
        Color(tc.toColorInt())
    }.getOrNull() ?: Color.White

    // Border color and width
    val borderColor = runCatching {
        val bc = cta?.container?.borderColor
        if (!bc.isNullOrBlank()) Color(bc.toColorInt()) else Color.Transparent
    }.getOrNull() ?: Color.Transparent
    
    val borderWidth = runCatching {
        val bw = cta?.container?.borderWidth
        if (!bw.isNullOrBlank()) {
            val width = bw.toFloatOrNull() ?: 0f
            if (width > 0f) width.dp else 0.dp
        } else 0.dp
    }.getOrNull() ?: 0.dp

    // Height
    val height = runCatching {
        val h = cta?.container?.height ?: pipStyling?.ctaHeight
        h?.toIntOrNull()?.dp ?: 48.dp
    }.getOrNull() ?: 48.dp

    // Corner radius per corner
    val cornerTopLeft = cta?.borderRadius?.topLeft?.dp ?: 0.dp
    val cornerTopRight = cta?.borderRadius?.topRight?.dp ?: 0.dp
    val cornerBottomRight = cta?.borderRadius?.bottomRight?.dp ?: 0.dp
    val cornerBottomLeft = cta?.borderRadius?.bottomLeft?.dp ?: 0.dp

    val shape = RoundedCornerShape(
        topStart = cornerTopLeft,
        topEnd = cornerTopRight,
        bottomEnd = cornerBottomRight,
        bottomStart = cornerBottomLeft
    )

    // Width handling: when both ctaFullWidth and ctaWidth are present, prefer ctaWidth for better control
    // This matches the backend JSON where ctaFullWidth: true and ctaWidth: 120 are both present
    val ctaWidth = cta?.container?.ctaWidth ?: pipStyling?.ctaWidth?.toIntOrNull()
    val ctaFullWidth = cta?.container?.ctaFullWidth ?: pipStyling?.ctaFullWidth ?: false
    
    // Font size
    val fontSize = cta?.text?.fontSize?.sp ?: pipStyling?.fontSize?.toIntOrNull()?.sp ?: 16.sp

    // Alignment: determine container alignment from backend
    val alignment = cta?.container?.alignment?.lowercase() ?: "center"
    
    val containerAlignment = when (alignment) {
        "left" -> Alignment.CenterStart
        "right" -> Alignment.CenterEnd
        "center", "middle" -> Alignment.Center
        else -> Alignment.Center
    }
    
    // Determine width modifier for the button
    val widthModifier = when {
        // If explicit width is provided, use it (even if fullWidth is also true)
        ctaWidth != null -> Modifier.width(ctaWidth.dp)
        // Otherwise, respect fullWidth flag
        ctaFullWidth -> Modifier.fillMaxWidth()
        // Default: wrap content
        else -> Modifier.wrapContentWidth()
    }
    
    // Shared click handler logic to avoid duplication
    val handleClick: () -> Unit = {
        // Note: link is guaranteed non-null/non-empty due to early return at line 43
        if (!AppStorys.isValidUrl(link)) {
            AppStorys.navigateToScreen(link)
        } else {
            AppStorys.openUrl(link)
        }
        onButtonClick()
    }
    
    // Conditional rendering based on width configuration:
    // - If explicit width is provided (e.g., 120dp), use outer Box for alignment
    // - If full width or wrap content, use single Box approach (like ModalBackendCta)
    if (ctaWidth != null && !ctaFullWidth) {
        // Fixed width button - use outer Box for alignment
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    top = marginTop,
                    bottom = marginBottom,
                    start = marginLeft,
                    end = marginRight
                ),
            contentAlignment = containerAlignment
        ) {
            // Inner clickable box (the actual button)
            Box(
                modifier = Modifier
                    .width(ctaWidth.dp)
                    .height(height)
                    .background(backgroundColor, shape)
                    .then(
                        if (borderWidth.value > 0f) {
                            Modifier.border(borderWidth, borderColor, shape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true),
                        onClick = handleClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    color = textColor,
                    fontSize = fontSize,
                    fontFamily = FontFamily.Default, // Keep font default as requested
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    } else {
        // Full width or wrap content - single Box (like ModalBackendCta)
        Box(
            modifier = modifier
                .padding(
                    top = marginTop,
                    bottom = marginBottom,
                    start = marginLeft,
                    end = marginRight
                )
                .then(widthModifier)
                .height(height)
                .background(backgroundColor, shape)
                .then(
                    if (borderWidth.value > 0f) {
                        Modifier.border(borderWidth, borderColor, shape)
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true),
                    onClick = handleClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buttonText,
                color = textColor,
                fontSize = fontSize,
                fontFamily = FontFamily.Default, // Keep font default as requested
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
