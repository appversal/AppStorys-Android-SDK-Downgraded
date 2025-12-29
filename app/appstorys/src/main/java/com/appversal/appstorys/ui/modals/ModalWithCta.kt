package com.appversal.appstorys.ui.modals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appversal.appstorys.api.Modal
import com.appversal.appstorys.ui.components.parseColorString
import androidx.compose.ui.graphics.Color

/**
 * Renders the title, subtitle and CTA row for a modal.
 * Keeps the same behavior/styling as the previous inline implementation in PopupModal.
 */
@Composable
fun ModalWithCta(
    modifier: Modifier = Modifier,
    modal: Modal,
    onPrimaryCta: ((link: String?) -> Unit)? = null,
    onSecondaryCta: ((link: String?) -> Unit)? = null,
) {
    // Title and subtitle
    val titleColor = parseColorString(modal.styling?.title?.color) ?: Color(0xFF3700FF)
    val titleSizeSp = modal.styling?.title?.size?.sp ?: 16.sp

    val subtitleColor = parseColorString(modal.styling?.subTitle?.color) ?: Color.Gray
    val subtitleSizeSp = modal.styling?.subTitle?.size?.sp ?: 12.sp

    val primaryBg = parseColorString(modal.styling?.primaryCta?.backgroundColor) ?: Color.Black
    val primaryTextColor = parseColorString(modal.styling?.primaryCta?.textColor) ?: Color.White
    val primaryHeight = (modal.styling?.primaryCta?.containerStyle?.height ?: 48).dp
    val primaryBorderWidth = (modal.styling?.primaryCta?.containerStyle?.borderWidth ?: 0).dp
    // borderColor lives on ModalCta not containerStyle
    val primaryBorderColor = parseColorString(modal.styling?.primaryCta?.borderColor) ?: Color.Transparent
    val primaryWidth = modal.styling?.primaryCta?.containerStyle?.ctaWidth?.dp

    val secondaryBg = parseColorString(modal.styling?.secondaryCta?.backgroundColor) ?: Color.DarkGray
    val secondaryTextColor = parseColorString(modal.styling?.secondaryCta?.textColor) ?: Color.White
    val secondaryHeight = (modal.styling?.secondaryCta?.containerStyle?.height ?: 48).dp
    val secondaryBorderWidth = (modal.styling?.secondaryCta?.containerStyle?.borderWidth ?: 0).dp
    val secondaryBorderColor = parseColorString(modal.styling?.secondaryCta?.borderColor) ?: Color.Transparent
    val secondaryWidth = modal.styling?.secondaryCta?.containerStyle?.ctaWidth?.dp

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        modal.content?.titleText?.let { title ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = titleColor,
                fontSize = titleSizeSp,
                textAlign = when (modal.styling?.title?.alignment?.trim()?.lowercase()) {
                    "left" -> TextAlign.Start
                    "right" -> TextAlign.End
                    else -> TextAlign.Center
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        modal.content?.subtitleText?.let { subtitle ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = subtitleSizeSp,
                textAlign = when (modal.styling?.subTitle?.alignment?.trim()?.lowercase()) {
                    "left" -> TextAlign.Start
                    "right" -> TextAlign.End
                    else -> TextAlign.Center
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // CTA buttons - reuse backend-provided spacing and occupyFullWidth flags
        val primaryOccupy = modal.styling?.primaryCta?.occupyFullWidth?.trim()?.equals("true", true) == true
        val secondaryOccupy = modal.styling?.secondaryCta?.occupyFullWidth?.trim()?.equals("true", true) == true

        val primaryMarginLeft = (modal.styling?.primaryCta?.spacing?.margin?.left ?: 0)
        val primaryMarginRight = (modal.styling?.primaryCta?.spacing?.margin?.right ?: 0)
        val primaryMarginTop = (modal.styling?.primaryCta?.spacing?.margin?.top ?: 0)
        val primaryMarginBottom = (modal.styling?.primaryCta?.spacing?.margin?.bottom ?: 0)

        val secondaryMarginLeft = (modal.styling?.secondaryCta?.spacing?.margin?.left ?: 0)
        val secondaryMarginRight = (modal.styling?.secondaryCta?.spacing?.margin?.right ?: 0)
        val secondaryMarginTop = (modal.styling?.secondaryCta?.spacing?.margin?.top ?: 0)
        val secondaryMarginBottom = (modal.styling?.secondaryCta?.spacing?.margin?.bottom ?: 0)

        val primaryMarginLeftDp = primaryMarginLeft.dp
        val primaryMarginRightDp = primaryMarginRight.dp
        val primaryMarginTopDp = primaryMarginTop.dp
        val primaryMarginBottomDp = primaryMarginBottom.dp

        val secondaryMarginLeftDp = secondaryMarginLeft.dp
        val secondaryMarginRightDp = secondaryMarginRight.dp
        val secondaryMarginTopDp = secondaryMarginTop.dp
        val secondaryMarginBottomDp = secondaryMarginBottom.dp

        val minHorizontalInset = 2.dp
        val effectivePrimaryMarginLeftDp = if (primaryOccupy && primaryMarginLeftDp == 0.dp) minHorizontalInset else primaryMarginLeftDp
        val effectivePrimaryMarginRightDp = if (primaryOccupy && primaryMarginRightDp == 0.dp) minHorizontalInset else primaryMarginRightDp

        val effectiveSecondaryMarginLeftDp = if (secondaryOccupy && secondaryMarginLeftDp == 0.dp) minHorizontalInset else secondaryMarginLeftDp
        val effectiveSecondaryMarginRightDp = if (secondaryOccupy && secondaryMarginRightDp == 0.dp) minHorizontalInset else secondaryMarginRightDp

        val rowStartInset = when {
            primaryOccupy && primaryMarginLeftDp == 0.dp -> minHorizontalInset
            !primaryOccupy && secondaryOccupy && secondaryMarginLeftDp == 0.dp -> minHorizontalInset
            else -> 0.dp
        }

        val rowEndInset = when {
            secondaryOccupy && secondaryMarginRightDp == 0.dp -> minHorizontalInset
            !secondaryOccupy && primaryOccupy && primaryMarginRightDp == 0.dp -> minHorizontalInset
            else -> 0.dp
        }

        val ctaBetweenSpacing = ((modal.styling?.primaryCta?.spacing?.margin?.right
            ?: modal.styling?.secondaryCta?.spacing?.margin?.left ?: 0)).dp

        val rowTopPadding = maxOf(primaryMarginTopDp, secondaryMarginTopDp)
        val rowBottomPadding = maxOf(primaryMarginBottomDp, secondaryMarginBottomDp)

        val rowArrangement = if (primaryOccupy || secondaryOccupy) {
            Arrangement.spacedBy(ctaBetweenSpacing)
        } else {
            Arrangement.spacedBy(ctaBetweenSpacing, Alignment.CenterHorizontally)
        }

        Row(
            modifier = Modifier
                .padding(start = rowStartInset, end = rowEndInset, top = rowTopPadding, bottom = rowBottomPadding)
                .fillMaxWidth(),
            horizontalArrangement = rowArrangement,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val primaryShape = RoundedCornerShape(
                topStart = (modal.styling?.primaryCta?.cornerRadius?.topLeft ?: 12).dp,
                topEnd = (modal.styling?.primaryCta?.cornerRadius?.topRight ?: 12).dp,
                bottomStart = (modal.styling?.primaryCta?.cornerRadius?.bottomLeft ?: 12).dp,
                bottomEnd = (modal.styling?.primaryCta?.cornerRadius?.bottomRight ?: 12).dp,
            )

            val secondaryShape = RoundedCornerShape(
                topStart = (modal.styling?.secondaryCta?.cornerRadius?.topLeft ?: 12).dp,
                topEnd = (modal.styling?.secondaryCta?.cornerRadius?.topRight ?: 12).dp,
                bottomStart = (modal.styling?.secondaryCta?.cornerRadius?.bottomLeft ?: 12).dp,
                bottomEnd = (modal.styling?.secondaryCta?.cornerRadius?.bottomRight ?: 12).dp,
            )

            val primaryTextSize = modal.styling?.primaryCta?.textStyle?.size?.sp ?: 14.sp
            val secondaryTextSize = modal.styling?.secondaryCta?.textStyle?.size?.sp ?: 14.sp

            modal.content?.primaryCtaText?.let { primaryText ->
                val content = modal.content
                val primaryTextAlign = when (modal.styling?.primaryCta?.containerStyle?.alignment) {
                    "left" -> TextAlign.Start
                    "right" -> TextAlign.End
                    else -> TextAlign.Center
                }

                val primaryBoxModifier = (if (primaryOccupy) Modifier.weight(1f) else Modifier)
                    .padding(
                        start = effectivePrimaryMarginLeftDp,
                        end = effectivePrimaryMarginRightDp,
                        top = primaryMarginTopDp,
                        bottom = primaryMarginBottomDp
                    )

                Box(modifier = primaryBoxModifier) {
                    BackendCta(
                        text = primaryText,
                        height = primaryHeight,
                        width = primaryWidth,
                        occupyFullWidth = primaryOccupy,
                        backgroundColor = primaryBg,
                        textColor = primaryTextColor,
                        textSizeSp = primaryTextSize.value.toInt(),
                        borderColor = primaryBorderColor,
                        borderWidth = primaryBorderWidth,
                        cornerRadius = primaryShape,
                        textAlign = primaryTextAlign,
                        modifier = Modifier
                    ) {
                        val link = content?.primaryCtaRedirection?.url ?: content?.primaryCtaRedirection?.value
                        onPrimaryCta?.invoke(link)
                    }
                }
            }

            modal.content?.secondaryCtaText?.let { secondaryText ->
                val content = modal.content
                val secondaryTextAlign = when (modal.styling?.secondaryCta?.containerStyle?.alignment) {
                    "left" -> TextAlign.Start
                    "right" -> TextAlign.End
                    else -> TextAlign.Center
                }

                val secondaryBoxModifier = (if (secondaryOccupy) Modifier.weight(1f) else Modifier)
                    .padding(
                        start = effectiveSecondaryMarginLeftDp,
                        end = effectiveSecondaryMarginRightDp,
                        top = secondaryMarginTopDp,
                        bottom = secondaryMarginBottomDp
                    )

                Box(modifier = secondaryBoxModifier) {
                    BackendCta(
                        text = secondaryText,
                        height = secondaryHeight,
                        width = secondaryWidth,
                        occupyFullWidth = secondaryOccupy,
                        backgroundColor = secondaryBg,
                        textColor = secondaryTextColor,
                        textSizeSp = secondaryTextSize.value.toInt(),
                        borderColor = secondaryBorderColor,
                        borderWidth = secondaryBorderWidth,
                        cornerRadius = secondaryShape,
                        textAlign = secondaryTextAlign,
                        modifier = Modifier
                    ) {
                        val link = content?.secondaryCtaRedirection?.url ?: content?.secondaryCtaRedirection?.value
                        onSecondaryCta?.invoke(link)
                    }
                }
            }

        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
