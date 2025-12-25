package com.appversal.appstorys.ui.components

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
import com.appversal.appstorys.ui.typography.mapFontStyle
import com.appversal.appstorys.ui.typography.mapFontWeight
import com.appversal.appstorys.ui.typography.rememberBackendFontFamily

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
    fontFamilyName: String? = null,
    fontWeightName: String? = null,
    fontStyleName: String? = null,
    textDecorationList: List<String>? = null,
    textAlign: TextAlign = TextAlign.Center,
    rotationDegrees: Float? = null,
    onClick: () -> Unit
) {
    // map weight/style using shared helpers
    val fontWeight = mapFontWeight(fontWeightName)
    val fontStyle = mapFontStyle(fontStyleName)

    // remember backend font family (uses GoogleFont provider internally)
    val fontFamily = rememberBackendFontFamily(
        fontFamilyName = fontFamilyName,
        weight = fontWeight,
        style = fontStyle
    )

    val textDecoration = if (textDecorationList?.any { it.equals("underline", true) } == true) TextDecoration.Underline else null

    // internal horizontal padding to give text breathing room inside the button
    val internalHorizontalPadding = 12.dp

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

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = textSizeSp.sp,
            textAlign = textAlign,
            maxLines = 1,
            style = TextStyle(
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                textDecoration = textDecoration
            )
        )

    }
}
