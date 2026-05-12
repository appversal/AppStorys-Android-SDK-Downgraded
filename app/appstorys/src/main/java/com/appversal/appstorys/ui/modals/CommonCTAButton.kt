package com.appversal.appstorys.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.api.ModalCta
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.common_components.parseColorString

/**
 * Configuration for a modal CTA button.
 * Contains all styling information extracted from ModalCta.
 */
data class ModalCTAButtonConfig(
    val text: String,
    val backgroundColor: Color,
    val textColor: String,
    val textSize: Int,
    val fontFamily: String?,
    val fontDecoration: List<String>?,
    val height: Dp,
    val width: Dp?,
    val occupyFullWidth: Boolean,
    val marginStart: Dp,
    val marginEnd: Dp,
    val marginTop: Dp,
    val marginBottom: Dp,
    val borderColor: Color,
    val borderWidth: Dp,
    val cornerRadiusTopLeft: Dp,
    val cornerRadiusTopRight: Dp,
    val cornerRadiusBottomLeft: Dp,
    val cornerRadiusBottomRight: Dp,
    val alignment: String,
    val redirectionUrl: String?
)

fun createModalCTAButtonConfig(
    text: String,
    styling: ModalCta?,
    redirectionUrl: String?,
    defaultHeight: Dp = 40.dp,
    defaultWidth: Dp? = 120.dp,
    defaultBackgroundColor: Color = Color.Black,
    defaultTextColor: String = "#FFFFFF",
    defaultTextSize: Int = 14,
    defaultCornerRadius: Int = 12
): ModalCTAButtonConfig {
    // Support both container and containerStyle
    val container = styling?.containerStyle ?: styling?.container

    // Support backgroundColor at root level or inside container
    val backgroundColor = parseColorString(styling?.backgroundColor ?: container?.backgroundColor)
        ?: defaultBackgroundColor

    // Support textColor at root level or inside text object
    val textColorString = styling?.textColor ?: styling?.text?.color ?: defaultTextColor

    // Support borderColor at root level or inside container
    val borderColor = parseColorString(styling?.borderColor ?: container?.borderColor)
        ?: Color.Transparent

    // Support textStyle.size or text.fontSize
    val textSizeSp = styling?.textStyle?.size ?: styling?.text?.fontSize ?: defaultTextSize

    // Support both occupyFullWidth and container.ctaFullWidth
    val occupyFullWidth = styling?.occupyFullWidth?.trim()?.equals("true", true) == true ||
        container?.ctaFullWidth == true

    // Margins - support both spacing.margin and margin directly
    val margin = styling?.spacing?.margin ?: styling?.margin
    val marginStart = (margin?.left ?: 0).dp
    val marginEnd = (margin?.right ?: 0).dp
    val marginTop = (margin?.top ?: 0).dp
    val marginBottom = (margin?.bottom ?: 0).dp

    // Corner radius
    val cornerRadiusTL = (styling?.cornerRadius?.topLeft ?: defaultCornerRadius).dp
    val cornerRadiusTR = (styling?.cornerRadius?.topRight ?: defaultCornerRadius).dp
    val cornerRadiusBL = (styling?.cornerRadius?.bottomLeft ?: defaultCornerRadius).dp
    val cornerRadiusBR = (styling?.cornerRadius?.bottomRight ?: defaultCornerRadius).dp

    // Height and width
    val height = (container?.height ?: defaultHeight.value.toInt()).dp
    val width = if (occupyFullWidth) null else (container?.ctaWidth ?: defaultWidth?.value?.toInt())?.dp

    // Button alignment
    val buttonAlignment = container?.alignment ?: "center"

    // Border width
    val borderWidth = (container?.borderWidth ?: 0).dp

    return ModalCTAButtonConfig(
        text = text,
        backgroundColor = backgroundColor,
        textColor = textColorString,
        textSize = textSizeSp,
        fontFamily = styling?.text?.fontFamily,
        fontDecoration = styling?.text?.fontDecoration,
        height = height,
        width = width,
        occupyFullWidth = occupyFullWidth,
        marginStart = marginStart,
        marginEnd = marginEnd,
        marginTop = marginTop,
        marginBottom = marginBottom,
        borderColor = borderColor,
        borderWidth = borderWidth,
        cornerRadiusTopLeft = cornerRadiusTL,
        cornerRadiusTopRight = cornerRadiusTR,
        cornerRadiusBottomLeft = cornerRadiusBL,
        cornerRadiusBottomRight = cornerRadiusBR,
        alignment = buttonAlignment,
        redirectionUrl = redirectionUrl
    )
}

@Composable
private fun RowScope.ModalCTAButtonInRow(
    config: ModalCTAButtonConfig,
    useWeight: Boolean,
    onClick: () -> Unit
) {
    val cornerRadius = RoundedCornerShape(
        topStart = config.cornerRadiusTopLeft,
        topEnd = config.cornerRadiusTopRight,
        bottomStart = config.cornerRadiusBottomLeft,
        bottomEnd = config.cornerRadiusBottomRight
    )

    // Build modifier chain for the Row (which IS the button)
    // Margins are applied directly from config (0 if not set by backend)
    val buttonModifier = (if (useWeight) Modifier.weight(1f) else Modifier)
        .padding(
            start = config.marginStart,
            end = config.marginEnd,
            top = config.marginTop,
            bottom = config.marginBottom
        )
        .then(
            when {
                config.occupyFullWidth -> Modifier.fillMaxWidth()
                config.width != null -> Modifier.width(config.width)
                else -> Modifier.wrapContentWidth()
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

    // Row as the button - centers content horizontally and vertically
    Row(
        modifier = buttonModifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommonText(
            modifier = Modifier.fillMaxWidth(),
            text = config.text,
            maxLines = 1,
            styling = TextStyling(
                color = config.textColor,
                fontSize = config.textSize,
                fontFamily = config.fontFamily ?: "",
                textAlign = "center",
                fontDecoration = config.fontDecoration
            )
        )
    }
}

/**
 * Common Modal CTA Button composable for single button use.
 * Wraps in a Row for alignment support.
 */
@Composable
fun ModalCTAButton(
    config: ModalCTAButtonConfig,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Determine horizontal arrangement from alignment
    val horizontalArrangement = when (config.alignment.lowercase()) {
        "left" -> Arrangement.Start
        "right" -> Arrangement.End
        "center", "middle" -> Arrangement.Center
        else -> Arrangement.Center
    }

    val cornerRadius = RoundedCornerShape(
        topStart = config.cornerRadiusTopLeft,
        topEnd = config.cornerRadiusTopRight,
        bottomStart = config.cornerRadiusBottomLeft,
        bottomEnd = config.cornerRadiusBottomRight
    )

    // Outer Row for alignment
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Button Row with styling
        Row(
            modifier = Modifier
                .padding(
                    start = config.marginStart,
                    end = config.marginEnd,
                    top = config.marginTop,
                    bottom = config.marginBottom
                )
                .then(
                    when {
                        config.occupyFullWidth -> Modifier.fillMaxWidth()
                        config.width != null -> Modifier.width(config.width)
                        else -> Modifier.wrapContentWidth()
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
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommonText(
                modifier = Modifier.fillMaxWidth(),
                text = config.text,
                maxLines = 1,
                styling = TextStyling(
                    color = config.textColor,
                    fontSize = config.textSize,
                    fontFamily = config.fontFamily ?: "",
                    textAlign = "center",
                    fontDecoration = config.fontDecoration
                )
            )
        }
    }
}

@Composable
fun ModalCTARow(
    primaryConfig: ModalCTAButtonConfig?,
    secondaryConfig: ModalCTAButtonConfig?,
    onPrimaryCta: ((link: String?) -> Unit)?,
    onSecondaryCta: ((link: String?) -> Unit)?
) {
    if (primaryConfig == null && secondaryConfig == null) return

    val hasBothButtons = primaryConfig != null && secondaryConfig != null

    // Check if buttons use full width
    val primaryFullWidth = primaryConfig?.occupyFullWidth == true
    val secondaryFullWidth = secondaryConfig?.occupyFullWidth == true

    // Determine row alignment (only matters when buttons don't fill full width)
    val containerAlignment = primaryConfig?.alignment ?: secondaryConfig?.alignment ?: "center"
    val rowArrangement = when (containerAlignment.lowercase()) {
        "left" -> Arrangement.Start
        "right" -> Arrangement.End
        "center", "middle" -> Arrangement.Center
        else -> Arrangement.Center  // Default to center
    }

    // When both buttons use full width, they split equally (weight)
    val useWeightForPrimary = hasBothButtons && primaryFullWidth
    val useWeightForSecondary = hasBothButtons && secondaryFullWidth

    Row(
        modifier = Modifier.fillMaxWidth(),
        // When using weights, start from left; otherwise use alignment
        horizontalArrangement = if (useWeightForPrimary || useWeightForSecondary) Arrangement.Start else rowArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Primary button
        primaryConfig?.let { config ->
            ModalCTAButtonInRow(
                config = config,
                useWeight = useWeightForPrimary,
                onClick = { onPrimaryCta?.invoke(config.redirectionUrl) }
            )
        }

        // Secondary button
        secondaryConfig?.let { config ->
            ModalCTAButtonInRow(
                config = config,
                useWeight = useWeightForSecondary,
                onClick = { onSecondaryCta?.invoke(config.redirectionUrl) }
            )
        }
    }
}
