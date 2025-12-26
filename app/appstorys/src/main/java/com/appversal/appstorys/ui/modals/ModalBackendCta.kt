package com.appversal.appstorys.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun BackendCta(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp,
    width: Dp? = null,
    occupyFullWidth: Boolean = false,
    backgroundColor: Color,
    textColor: Color,
    textSizeSp: Int,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = Dp.Hairline,
    cornerRadius: RoundedCornerShape,
    fontWeightName: String? = null,
    fontStyleName: String? = null,
    textDecorationList: List<String>? = null,
    textAlign: TextAlign = TextAlign.Center,
    rotationDegrees: Float? = null,
    // optional explicit alignment for the CTA container (e.g. "left", "right", "center").
    // if null, we fall back to `textAlign`.
    buttonAlignment: String? = null,
    onClick: () -> Unit
) {
    // map weight/style using shared helpers
    val fontWeight = mapFontWeight(fontWeightName)
    val fontStyle = mapFontStyle(fontStyleName)

    // use default font family for now (no backend resolver)
    val fontFamily = FontFamily.Default

    val textDecoration = if (textDecorationList?.any { it.equals("underline", true) } == true) TextDecoration.Underline else null

    // internal horizontal padding to give text breathing room inside the button
    val internalHorizontalPadding = 0.dp

    var baseModifier = modifier
        .then(
            when {
                occupyFullWidth -> Modifier.fillMaxWidth()
                width != null -> Modifier.width(width)
                else -> Modifier
            }
        )
        .height(height)
        .padding(horizontal = internalHorizontalPadding)
        .background(backgroundColor, cornerRadius)
        .then(if (borderWidth.value > 0f) Modifier.border(borderWidth, borderColor, cornerRadius) else Modifier)
        .clickable(
            role = Role.Button,
            interactionSource = remember { MutableInteractionSource() },
            indication = rememberRipple(bounded = true),
            onClick = onClick
        )

    if (rotationDegrees != null && rotationDegrees != 0f) {
        baseModifier = baseModifier.rotate(rotationDegrees)
    }

    // determine box content alignment from explicit buttonAlignment or fallback to textAlign
    val boxContentAlignment = when (buttonAlignment?.lowercase()) {
        "left" -> Alignment.CenterStart
        "right" -> Alignment.CenterEnd
        "center", "middle" -> Alignment.Center
        else -> when (textAlign) {
            TextAlign.Left -> Alignment.CenterStart
            TextAlign.Right -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    }

    Box(
        modifier = baseModifier,
        contentAlignment = boxContentAlignment
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = textSizeSp.sp,
            textAlign = textAlign,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = internalHorizontalPadding),
            style = TextStyle(
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration
            )
        )

    }
}

// Move map helpers here so callers don't need the resolver file
fun mapFontWeight(value: String?): FontWeight = when (value?.lowercase()) {
    "bold", "700", "800" -> FontWeight.Bold
    "600" -> FontWeight.SemiBold
    "500" -> FontWeight.Medium
    else -> FontWeight.Normal
}

fun mapFontStyle(value: String?): FontStyle =
    if (value?.equals("italic", true) == true)
        FontStyle.Italic
    else FontStyle.Normal