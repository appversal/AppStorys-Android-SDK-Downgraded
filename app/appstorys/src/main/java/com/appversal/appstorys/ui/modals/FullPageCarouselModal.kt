package com.appversal.appstorys.ui.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.appversal.appstorys.api.ModalContent
import com.appversal.appstorys.api.ModalDetails
import com.appversal.appstorys.ui.AutoSlidingCarousel
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.createCrossButtonConfig
import com.appversal.appstorys.ui.common_components.parseColorString
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.api.WidgetDetails
import com.appversal.appstorys.ui.DotsIndicator
import com.appversal.appstorys.ui.common_components.CommonText

@OptIn(ExperimentalFoundationApi::class)
@Composable

internal fun FullPageCarouselModal(
    onCloseClick: () -> Unit,
    modalDetails: ModalDetails,
    onModalClick: () -> Unit,
    onPrimaryCta: ((link: String?) -> Unit)? = null,
    onSecondaryCta: ((link: String?) -> Unit)? = null,
) {

    val modal = modalDetails.modals?.getOrNull(0) ?: return

    // Determine slides: prefer content.set if present, else fallback to single content as a list
    val slides: List<ModalContent> = modal.content?.set?.takeIf { it.isNotEmpty() } ?: listOfNotNull(modal.content)

    val context = LocalContext.current

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { slides.size })

    // Get current slide for dynamic appearance
    val currentSlideIndex = pagerState.currentPage
    val currentSlide = slides.getOrNull(currentSlideIndex)

    // Resolve appearance dynamically based on current slide
    val currentSlideAppearance = currentSlide?.styling?.appearance
    val effectiveAppearance = currentSlideAppearance ?: modal.styling?.appearance

    // backdrop color/opacity may be provided in multiple forms; prefer structured `backdrop` then fallback to flat fields
    val backdropColorString = effectiveAppearance?.backdrop?.color ?: effectiveAppearance?.backdropColor
    val backdropOpacityString = effectiveAppearance?.backdrop?.opacity ?: effectiveAppearance?.backdropOpacity

    val rawBackdropOpacityFinal = backdropOpacityString?.toFloatOrNull() ?: 30f
    val backdropAlphaFinal = if (effectiveAppearance?.enableBackdrop == false) 0f else (rawBackdropOpacityFinal / 100f).coerceIn(0f,1f)
    val backdropColorFinal = parseColorString(backdropColorString) ?: Color.Black

    // Check if first slide media is loaded before showing the modal
    val firstSlideMediaUrl = slides.firstOrNull()?.resolveMediaUrl()
    val firstSlideLoadState = rememberMediaLoadState(firstSlideMediaUrl)
    val isFirstSlideLoaded = firstSlideLoadState is MediaLoadState.Success || firstSlideMediaUrl.isNullOrEmpty()

    // Use alpha to control visibility - prevents animation glitches
    val contentAlpha = if (isFirstSlideLoaded) 1f else 0f

    Dialog(
        onDismissRequest = onCloseClick,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        // Always render the structure, but control visibility with alpha
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
                .background(backdropColorFinal.copy(alpha = backdropAlphaFinal)),
        ) {
            Column(modifier = Modifier.fillMaxSize()
            ){

                // 🔵 MEDIA SECTION
                Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),   // media takes available space
                        contentAlignment = Alignment.Center  // Center the media vertically and horizontally
                    ) {
                        AutoSlidingCarousel(
                            useDots = false,
                            autoScrollEnabled = false,
                            pagerState = pagerState,
                            itemsCount = slides.size,
                            modifier = Modifier.fillMaxSize(), // Fill entire area to capture swipe gestures
                            widgetDetails = WidgetDetails(
                                id = null,
                                type = null,
                                width = null,
                                height = null,
                                widgetImages = null,
                                campaign = null,
                                screen = null,
                                styling = null
                            ),
                            itemContent = { index ->
                                val slide = slides[index]
                                // Uses resolveMediaUrl() from ModalMediaUtils.kt
                                val slideMediaUrl = slide.resolveMediaUrl()

                                // Box fills entire pager area so swipe works everywhere
                                // Media is centered within this area
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val mediaModifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()

                                    ModalMediaRendererWithCallback(
                                        mediaUrl = slideMediaUrl,
                                        modifier = mediaModifier,
                                        contentDescription = slide.titleText,
                                        muted = false,
                                        onMediaRendered = {}
                                    )
                                }
                            }
                        )
                    }

                    // 🔵 DOTS — BELOW MEDIA, ABOVE TEXT (NO align())
                    if (slides.size > 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            DotsIndicator(
                                totalDots = slides.size,
                                selectedIndex =
                                    if (pagerState.isScrollInProgress)
                                        pagerState.currentPage
                                    else
                                        pagerState.targetPage,
                                selectedColor = Color.White,
                                unSelectedColor = Color.White.copy(alpha = 0.5f),
                                dotSize = 8.dp,
                                selectedLength = 20.dp
                            )
                        }
                    }


                    currentSlide?.let { slide ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            // Title and Subtitle inside padded area
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                // Resolve styling from slide, else fallback to modal styling
                                val titleStyling = slide.styling?.title ?: modal.styling?.title
                                val subtitleStyling = slide.styling?.subTitle ?: modal.styling?.subTitle

                            slide.titleText?.let { title ->
                                Spacer(modifier = Modifier.height(12.dp))

                                CommonText(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = title,
                                    styling = TextStyling(
                                        color = titleStyling?.color,
                                        fontSize = titleStyling?.fontSize ?: titleStyling?.size ?: 18,
                                        fontFamily = titleStyling?.fontFamily ?: titleStyling?.font ?: "",
                                        textAlign = titleStyling?.textAlign ?: titleStyling?.alignment?.trim()?.lowercase(),
                                        fontDecoration = titleStyling?.fontDecoration
                                    )
                                )
                            }

                            slide.subtitleText?.let { subtitle ->
                                Spacer(modifier = Modifier.height(6.dp))

                                CommonText(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = subtitle,
                                    styling = TextStyling(
                                        color = subtitleStyling?.color,
                                        fontSize = subtitleStyling?.fontSize ?: subtitleStyling?.size ?: 14,
                                        fontFamily = subtitleStyling?.fontFamily ?: subtitleStyling?.font ?: "",
                                        textAlign = subtitleStyling?.textAlign ?: subtitleStyling?.alignment?.trim()?.lowercase(),
                                        fontDecoration = subtitleStyling?.fontDecoration
                                    )
                                )
                            }

                            // Spacer before CTAs
                            Spacer(modifier = Modifier.height(12.dp))
                            }

                        // CTAs - OUTSIDE the content padding so they can extend to edges
                        // Resolve CTA text with fallbacks (slide -> modal)
                        val primaryText = slide.primaryCtaText
                            ?: slide.primaryCta
                            ?: modal.content?.primaryCtaText
                            ?: modal.content?.primaryCta

                        val secondaryText = slide.secondaryCtaText
                            ?: slide.secondayCta
                            ?: slide.secondaryCtaAlt
                            ?: modal.content?.secondaryCtaText
                            ?: modal.content?.secondaryCtaAlt

                        val primaryStyling = slide.styling?.primaryCta ?: modal.styling?.primaryCta
                        val secondaryStyling = slide.styling?.secondaryCta ?: modal.styling?.secondaryCta

                        // Create CTA configs using the common helper with fallback-resolved text
                        val primaryConfig = if (!primaryText.isNullOrEmpty()) {
                            val link = slide.primaryCtaRedirection?.url ?: slide.primaryCtaRedirection?.value
                            createModalCTAButtonConfig(
                                text = primaryText,
                                styling = primaryStyling,
                                redirectionUrl = link,
                                defaultHeight = 48.dp,
                                defaultWidth = null,
                                defaultBackgroundColor = Color.Black
                            )
                        } else null

                        val secondaryConfig = if (!secondaryText.isNullOrEmpty()) {
                            val link = slide.secondaryCtaRedirection?.url ?: slide.secondaryCtaRedirection?.value
                            createModalCTAButtonConfig(
                                text = secondaryText,
                                styling = secondaryStyling,
                                redirectionUrl = link,
                                defaultHeight = 48.dp,
                                defaultWidth = null,
                                defaultBackgroundColor = Color.DarkGray
                            )
                        } else null

                        // Use the common CTA row
                        ModalCTARow(
                            primaryConfig = primaryConfig,
                            secondaryConfig = secondaryConfig,
                            onPrimaryCta = onPrimaryCta,
                            onSecondaryCta = onSecondaryCta
                        )
                    }
            }
            }


            // Cross button: use current slide's crossButton config
            val currentSlideCrossButton = currentSlide?.styling?.crossButton
            val effectiveCrossButton = currentSlideCrossButton ?: modal.styling?.crossButton

            val crossButtonImageUrl = effectiveCrossButton?.uploadImage?.url
                ?: effectiveCrossButton?.image
            // Normalize size from multiple possible payload shapes
            val crossButtonSize = effectiveCrossButton?.size
            // Support both "color" and "colors" fields, as well as default.color
            val crossColors = effectiveCrossButton?.color ?: effectiveCrossButton?.colors
            val crossMargin = effectiveCrossButton?.margin

            val crossConfig = createCrossButtonConfig(
                fillColorString = crossColors?.fill,
                crossColorString = crossColors?.cross,
                strokeColorString = crossColors?.stroke,
                marginTop = crossMargin?.top,
                marginEnd = crossMargin?.right,
                size = crossButtonSize,
                imageUrl = crossButtonImageUrl
            )

            // show cross button only if enabled (defaults to true)
            // The enabled flag comes from styling.crossButton.enabled
            val crossEnableFlag = effectiveCrossButton?.enabled
                ?: effectiveCrossButton?.enableCrossButton
            // Show cross button if enabled is true (default behavior) - no need to require custom resources
            // The common CrossButton component will use a default cross icon if no custom image is provided
            val showCross = crossEnableFlag != false

            // Only show cross button after media has loaded
            // Using isFirstSlideLoaded which is more reliable than render callbacks
            if (showCross && isFirstSlideLoaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    CrossButton(
                        config = crossConfig,
                        onClose = onCloseClick
                    )
                }
            }
        }
    }
}
