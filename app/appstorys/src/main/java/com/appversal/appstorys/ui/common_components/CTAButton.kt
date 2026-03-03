package com.appversal.appstorys.ui.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.api.TextStyling

data class CTAButtonConfig(
    // Text styling
    val textColor: String = "#FFFFFF",
    val textSize: Int = 14,
    val fontFamily: String? = null,
    val fontDecoration: List<String>? = null,

    // Margins
    val marginTop: Dp = 0.dp,
    val marginEnd: Dp = 0.dp,
    val marginBottom: Dp = 0.dp,
    val marginStart: Dp = 0.dp,

    // Container
    val height: Dp = 40.dp,
    val width: Dp? = null,
    val alignment: String = "center",
    val borderColor: Color = Color.Transparent,
    val borderWidth: Dp = 0.dp,
    val fullWidth: Boolean = false,
    val backgroundColor: Color = Color.Black,

    // Border Radius
    val borderRadiusTopLeft: Dp = 12.dp,
    val borderRadiusTopRight: Dp = 12.dp,
    val borderRadiusBottomLeft: Dp = 12.dp,
    val borderRadiusBottomRight: Dp = 12.dp,

    // Optional rotation
    val rotationDegrees: Float = 0f
)

/**
 * CTAButton - A common Call-To-Action button component
 * Following the same architecture as CrossButton and other common buttons
 *
 * The alignment property controls the horizontal position of the button container:
 * - "left" -> button aligned to start
 * - "center" -> button centered (default)
 * - "right" -> button aligned to end
 *
 * Text inside the button is always centered.
 * Margins are applied from the parent container boundaries.
 */
@Composable
fun CTAButton(
    text: String,
    modifier: Modifier = Modifier,
    config: CTAButtonConfig = CTAButtonConfig(),
    onClick: () -> Unit
) {
    val cornerRadius = RoundedCornerShape(
        topStart = config.borderRadiusTopLeft,
        topEnd = config.borderRadiusTopRight,
        bottomStart = config.borderRadiusBottomLeft,
        bottomEnd = config.borderRadiusBottomRight
    )

    // Determine horizontal arrangement from config
    val horizontalArrangement = when (config.alignment.lowercase()) {
        "left" -> Arrangement.Start
        "right" -> Arrangement.End
        "center", "middle" -> Arrangement.Center
        else -> Arrangement.Center
    }

    var buttonModifier = Modifier
        .then(
            when {
                config.fullWidth -> Modifier.fillMaxWidth()
                config.width != null -> Modifier.width(config.width)
                else -> Modifier
            }
        )
        .height(config.height)
        .background(config.backgroundColor, cornerRadius)
        .then(
            if (config.borderWidth.value > 0f) {
                Modifier.border(config.borderWidth, config.borderColor, cornerRadius)
            } else {
                Modifier
            }
        )
        .clickable(
            role = Role.Button,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )

    if (config.rotationDegrees != 0f) {
        buttonModifier = buttonModifier.rotate(config.rotationDegrees)
    }

    // Outer Row for horizontal alignment with margins from parent boundaries
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = config.marginTop,
                end = config.marginEnd,
                bottom = config.marginBottom,
                start = config.marginStart
            ),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Button box
        Box(
            modifier = buttonModifier,
            contentAlignment = Alignment.Center  // Always center content inside the button
        ) {
            CommonText(
                modifier = Modifier.fillMaxWidth(),
                text = text,
                maxLines = 1,
                styling = TextStyling(
                    color = config.textColor,
                    fontSize = config.textSize,
                    fontFamily = config.fontFamily ?: "",
                    textAlign = "center",  // Text inside button should always be centered
                    fontDecoration = config.fontDecoration
                )
            )
        }
    }
}

/**
 * Factory function to create CTAButtonConfig from raw values
 */
fun createCTAButtonConfig(
    // Text styling
    textColor: String? = null,
    textSize: Int? = null,
    fontFamily: String? = null,
    fontDecoration: List<String>? = null,

    // Margins
    marginTop: Int? = null,
    marginEnd: Int? = null,
    marginBottom: Int? = null,
    marginStart: Int? = null,

    // Container
    height: Int? = null,
    width: Int? = null,
    alignment: String? = null,
    borderColorString: String? = null,
    borderWidth: Int? = null,
    fullWidth: Boolean? = null,
    backgroundColorString: String? = null,

    // Border Radius
    borderRadiusTopLeft: Int? = null,
    borderRadiusTopRight: Int? = null,
    borderRadiusBottomLeft: Int? = null,
    borderRadiusBottomRight: Int? = null,

    // Optional rotation
    rotationDegrees: Float? = null
): CTAButtonConfig {
    return CTAButtonConfig(
        textColor = textColor ?: "#FFFFFF",
        textSize = textSize ?: 14,
        fontFamily = fontFamily,
        fontDecoration = fontDecoration,

        marginTop = marginTop?.dp ?: 0.dp,
        marginEnd = marginEnd?.dp ?: 0.dp,
        marginBottom = marginBottom?.dp ?: 0.dp,
        marginStart = marginStart?.dp ?: 0.dp,

        height = height?.dp ?: 40.dp,
        width = width?.dp,
        alignment = alignment ?: "center",
        borderColor = parseColorString(borderColorString) ?: Color.Transparent,
        borderWidth = borderWidth?.dp ?: 0.dp,
        fullWidth = fullWidth ?: false,
        backgroundColor = parseColorString(backgroundColorString) ?: Color.Black,

        borderRadiusTopLeft = borderRadiusTopLeft?.dp ?: 12.dp,
        borderRadiusTopRight = borderRadiusTopRight?.dp ?: 12.dp,
        borderRadiusBottomLeft = borderRadiusBottomLeft?.dp ?: 12.dp,
        borderRadiusBottomRight = borderRadiusBottomRight?.dp ?: 12.dp,

        rotationDegrees = rotationDegrees ?: 0f
    )
}

// Helper functions for font mapping
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

