package com.appversal.appstorys.ui.scratchcard

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.appversal.appstorys.api.TextStyling
import com.appversal.appstorys.ui.common_components.CommonText
import com.appversal.appstorys.ui.common_components.CrossButton
import com.appversal.appstorys.ui.common_components.CrossButtonConfig
import kotlin.math.min
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.appversal.appstorys.api.ScratchCardDetails

/**
 * Everything [CardScratch] reads out of a SCRT payload, resolved once.
 *
 * This parsing used to sit inline in the composable, so ~350 lines of
 * `?.get(...)?.jsonPrimitive?.intOrNull ?: default` re-ran on every recomposition —
 * and CardScratch recomposes on every pointer event while the card is scratched.
 * [from] is a pure function, so it is also the first part of SCRT that can be
 * unit-tested against a payload fixture.
 *
 * The parsing itself is unchanged: the bodies below were moved verbatim.
 */
internal data class ScratchCardConfig(
    val cardHeight: Int?,
    val overlayImage: String,
    val haptics: Boolean?,
    val hapticsEnabled: Boolean,
    val bannerImage: String,
    val offerTitle: String,
    val titleFontSize: Int,
    val offerTitleColor: String,
    val offerTitleFontFamily: String,
    val offerTitleFontDecoration: List<String>,
    val offerTitleTextAlign: String,
    val offerTitleMarginTop: Dp,
    val offerTitleMarginBottom: Dp,
    val offerTitleMarginLeft: Dp,
    val offerTitleMarginRight: Dp,
    val offerSubtitle: String,
    val subtitleFontSize: Int,
    val offerSubtitleColor: String,
    val offerSubtitleFontFamily: String,
    val offerSubtitleFontDecoration: List<String>,
    val offerSubtitleTextAlign: String,
    val offerSubtitleMarginTop: Dp,
    val offerSubtitleMarginBottom: Dp,
    val offerSubtitleMarginLeft: Dp,
    val offerSubtitleMarginRight: Dp,
    val onlyImage: Boolean,
    val rewardBgColor: String,
    val couponCode: String,
    val couponBgColor: String,
    val couponBorderColor: String,
    val couponBorderWidth: Int,
    val couponAlignment: String,
    val couponCtaFullWidth: Boolean,
    val couponCtaWidth: Dp,
    val couponHeight: Dp,
    val couponTextColor: String,
    val couponFontSize: Int,
    val couponFontFamily: String,
    val couponFontDecoration: List<String>,
    val couponTopLeft: Dp,
    val couponTopRight: Dp,
    val couponBottomLeft: Dp,
    val couponBottomRight: Dp,
    val couponMarginTop: Dp,
    val couponMarginBottom: Dp,
    val couponMarginLeft: Dp,
    val couponMarginRight: Dp,
    val customSoundEnabled: Boolean,
    val ctaHeight: Dp,
    val ctaColor: String,
    val ctaBorderColor: String,
    val ctaBorderWidth: Int,
    val ctaAlignment: String,
    val ctaFullWidth: Boolean,
    val ctaWidth: Dp,
    val ctaText: String,
    val ctaTextColor: String,
    val ctaFontSize: Int,
    val ctaFontFamily: String,
    val ctaFontDecoration: List<String>,
    val topLeft: Dp,
    val topRight: Dp,
    val bottomLeft: Dp,
    val bottomRight: Dp,
    val ctaPaddingTop: Dp,
    val ctaPaddingBottom: Dp,
    val ctaPaddingLeft: Dp,
    val ctaPaddingRight: Dp,
    val termsAndConditionsHtml: String,
    val crossEnabled: Boolean,
    val crossAlignment: String,
    val crossMarginBottom: Dp,
    val crossFillColor: String?,
    val crossColor: String?,
    val crossStrokeColor: String?,
    val crossMarginTop: Int?,
    val crossMarginEnd: Int?,
    val crossSize: Int?,
    val crossImageUrl: String?,
    val cardWidth: Dp,
    val cardHeightDp: Dp,
    val cornerRadius: Dp,
    val imageWidth: Dp,
    val imageHeight: Dp,
    val imageTopLeft: Dp,
    val imageTopRight: Dp,
    val imageBottomLeft: Dp,
    val imageBottomRight: Dp,
    val imageMarginTop: Dp,
    val imageMarginBottom: Dp,
    val imageMarginLeft: Dp,
    val imageMarginRight: Dp
) {
    companion object {
        fun from(scratchCardDetails: ScratchCardDetails, screenWidth: Dp): ScratchCardConfig {
    val details = scratchCardDetails.content

    // -------- card_size --------
    val cardSizeData = details
        ?.get("card_size")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val cardHeight = cardSizeData
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull

    // -------- overlay_image (coverImage at root level) --------
    val overlayImage = scratchCardDetails.coverImage ?: ""

    // -------- interactions --------
    val interactions = details
        ?.get("interactions")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val haptics = interactions
        ?.get("haptics")
        ?.jsonPrimitive
        ?.booleanOrNull

    // Alternative haptic fields
    val hapticFeedbackEnabled = details
        ?.get("haptic_feedback_enabled")
        ?.jsonPrimitive
        ?.booleanOrNull

    val hapticStyle = details
        ?.get("haptic_style")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    // Use haptic_feedback_enabled if available, otherwise fall back to interactions.haptics
    val hapticsEnabled = hapticFeedbackEnabled ?: haptics ?: false

    // -------- reward_content --------
    val rewardContent = details
        ?.get("reward_content")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    // bannerImage at root level
    val bannerImage = scratchCardDetails.bannerImage ?: ""

    // NEW: offerTitle is now an object with text and textStyle
    val offerTitleObj = rewardContent
        ?.get("offerTitle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val offerTitle = offerTitleObj
        ?.get("text")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""
    val offerTitleTextStyle = offerTitleObj
        ?.get("textStyle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val titleFontSize = offerTitleTextStyle
        ?.get("fontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 18
    val offerTitleColor = offerTitleTextStyle
        ?.get("color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#000000"
    val offerTitleFontFamily = offerTitleTextStyle
        ?.get("fontFamily")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""
    val offerTitleFontDecoration = try {
        offerTitleTextStyle?.get("fontDecoration")?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: listOf()
    } catch (e: Exception) {
        listOf()
    }
    val offerTitleTextAlign = offerTitleTextStyle
        ?.get("textAlign")
        ?.jsonPrimitive
        ?.contentOrNull ?: "center"
    val offerTitleMargin = offerTitleTextStyle
        ?.get("margin")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val offerTitleMarginTop = offerTitleMargin?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerTitleMarginBottom =
        offerTitleMargin?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerTitleMarginLeft = offerTitleMargin?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerTitleMarginRight = offerTitleMargin?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp

    // NEW: offerSubtitle is now an object with text and textStyle
    val offerSubtitleObj = rewardContent
        ?.get("offerSubtitle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val offerSubtitle = offerSubtitleObj
        ?.get("text")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""
    val offerSubtitleTextStyle = offerSubtitleObj
        ?.get("textStyle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val subtitleFontSize = offerSubtitleTextStyle
        ?.get("fontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 14
    val offerSubtitleColor = offerSubtitleTextStyle
        ?.get("color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#000000"
    val offerSubtitleFontFamily = offerSubtitleTextStyle
        ?.get("fontFamily")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""
    val offerSubtitleFontDecoration = try {
        offerSubtitleTextStyle?.get("fontDecoration")?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: listOf()
    } catch (e: Exception) {
        listOf()
    }
    val offerSubtitleTextAlign = offerSubtitleTextStyle
        ?.get("textAlign")
        ?.jsonPrimitive
        ?.contentOrNull ?: "center"
    val offerSubtitleMargin = offerSubtitleTextStyle
        ?.get("margin")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val offerSubtitleMarginTop =
        offerSubtitleMargin?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerSubtitleMarginBottom =
        offerSubtitleMargin?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerSubtitleMarginLeft =
        offerSubtitleMargin?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val offerSubtitleMarginRight =
        offerSubtitleMargin?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp

    // NEW: onlyImage is now a boolean directly
    val onlyImage = rewardContent
        ?.get("onlyImage")
        ?.jsonPrimitive
        ?.booleanOrNull ?: false

    val rewardBgColor = rewardContent
        ?.get("background_color")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.ifEmpty { "#FFFFFF" } ?: "#FFFFFF"

    // -------- coupon_code (now at root level of details) --------
    val couponCode = scratchCardDetails.coupon_code ?: ""

    // -------- couponCodeCta (replaces old coupon object, now inside reward_content) --------
    val couponCodeCta = rewardContent
        ?.get("couponCodeCta")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val couponContainer = couponCodeCta
        ?.get("container")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val couponTextObj = couponCodeCta
        ?.get("text")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val couponCornerRadiusObj = couponCodeCta
        ?.get("cornerRadius")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject
    val couponMarginObj = couponCodeCta
        ?.get("margin")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val couponBgColor = couponContainer
        ?.get("backgroundColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#30d158"

    val couponBorderColor = couponContainer
        ?.get("borderColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#fa6837"

    val couponBorderWidth = couponContainer
        ?.get("borderWidth")
        ?.jsonPrimitive
        ?.intOrNull ?: 1

    val couponAlignment = couponContainer
        ?.get("alignment")
        ?.jsonPrimitive
        ?.contentOrNull ?: "center"

    val couponCtaFullWidth = couponContainer
        ?.get("ctaFullWidth")
        ?.jsonPrimitive
        ?.booleanOrNull ?: false

    val couponCtaWidth = couponContainer
        ?.get("ctaWidth")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified

    val couponHeight = couponContainer
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified

    val couponTextColor = couponTextObj
        ?.get("color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#008932"

    val couponFontSize = couponTextObj
        ?.get("fontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 14

    val couponFontFamily = couponTextObj
        ?.get("fontFamily")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val couponFontDecoration = try {
        couponTextObj?.get("fontDecoration")?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: listOf()
    } catch (e: Exception) {
        listOf()
    }

    val couponTopLeft = couponCornerRadiusObj?.get("topLeft")?.jsonPrimitive?.intOrNull?.dp ?: 8.dp
    val couponTopRight =
        couponCornerRadiusObj?.get("topRight")?.jsonPrimitive?.intOrNull?.dp ?: 8.dp
    val couponBottomLeft =
        couponCornerRadiusObj?.get("bottomLeft")?.jsonPrimitive?.intOrNull?.dp ?: 8.dp
    val couponBottomRight =
        couponCornerRadiusObj?.get("bottomRight")?.jsonPrimitive?.intOrNull?.dp ?: 8.dp

    val couponMarginTop = couponMarginObj?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val couponMarginBottom = couponMarginObj?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val couponMarginLeft = couponMarginObj?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val couponMarginRight = couponMarginObj?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp

    // -------- custom_sound_enabled --------
    val customSoundEnabled = details
        ?.get("custom_sound_enabled")
        ?.jsonPrimitive
        ?.booleanOrNull ?: true

    // -------- cta --------
    val cta = details
        ?.get("cta")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val container = cta?.get("container")?.takeIf { it !is JsonNull }?.jsonObject
    val textObj = cta?.get("text")?.takeIf { it !is JsonNull }?.jsonObject
    val cornerRadiusObj = cta?.get("cornerRadius")?.takeIf { it !is JsonNull }?.jsonObject
    val marginObj = cta?.get("margin")?.takeIf { it !is JsonNull }?.jsonObject

    val ctaHeight = container
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 48.dp

    val ctaColor = container
        ?.get("backgroundColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#0066FF"

    val ctaBorderColor = container
        ?.get("borderColor")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val ctaBorderWidth = container
        ?.get("borderWidth")
        ?.jsonPrimitive
        ?.intOrNull ?: 0

    val ctaAlignment = container
        ?.get("alignment")
        ?.jsonPrimitive
        ?.contentOrNull ?: "center"

    val ctaFullWidth = container
        ?.get("ctaFullWidth")
        ?.jsonPrimitive
        ?.booleanOrNull ?: false

    val ctaWidth = container
        ?.get("ctaWidth")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified


    val ctaText = scratchCardDetails.button_text ?: "Claim offer"


    val ctaTextColor = textObj
        ?.get("color")
        ?.jsonPrimitive
        ?.contentOrNull ?: "#FFFFFF"

    val ctaFontSize = textObj
        ?.get("fontSize")
        ?.jsonPrimitive
        ?.intOrNull ?: 16

    val ctaFontFamily = textObj
        ?.get("fontFamily")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""

    val ctaFontDecoration = try {
        textObj?.get("fontDecoration")?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: listOf()
    } catch (e: Exception) {
        listOf()
    }


    val topLeft = cornerRadiusObj?.get("topLeft")?.jsonPrimitive?.intOrNull?.dp ?: 12.dp
    val topRight = cornerRadiusObj?.get("topRight")?.jsonPrimitive?.intOrNull?.dp ?: 12.dp
    val bottomLeft = cornerRadiusObj?.get("bottomLeft")?.jsonPrimitive?.intOrNull?.dp ?: 12.dp
    val bottomRight = cornerRadiusObj?.get("bottomRight")?.jsonPrimitive?.intOrNull?.dp ?: 12.dp


    val ctaPaddingTop = marginObj?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 4.dp
    val ctaPaddingBottom = marginObj?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 4.dp
    val ctaPaddingLeft = marginObj?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 4.dp
    val ctaPaddingRight = marginObj?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 4.dp


    // -------- terms_and_conditions (HTML string) --------
    val termsAndConditionsHtml = details
        ?.get("terms_and_conditions")
        ?.jsonPrimitive
        ?.contentOrNull ?: ""


    // Get configured width, respecting screen boundaries (industry standard: max 95% of screen width)
    val maxCardWidth = screenWidth * 0.90f
    val configuredCardWidth = cardSizeData
        ?.get("width")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: min(screenWidth.value * 0.85f, 320f).dp

    // Clamp width to screen bounds - industry standard approach
    val cardWidth = minOf(configuredCardWidth, maxCardWidth)

    // Use cardHeight from card_size. The dashboard only ever sends width, so the
    // fallback decides the card shape — 300.dp matches the Flutter SDK
    // (scratch_card.dart: cardSizeHeight ?? 300.0) so one payload renders the
    // same on both platforms.
    val cardHeightDp = cardHeight?.dp ?: 300.dp
    val cornerRadius = cardSizeData
        ?.get("corner_radius")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: 32.dp

    // -------- imageCircle --------
    val imageCircleObj = rewardContent
        ?.get("imageCircle")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val imageSizeObj = imageCircleObj
        ?.get("size")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val imageWidth = imageSizeObj
        ?.get("width")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified

    val imageHeight = imageSizeObj
        ?.get("height")
        ?.jsonPrimitive
        ?.intOrNull
        ?.dp ?: Dp.Unspecified

    val imageCornerObj = imageCircleObj
        ?.get("cornerRadius")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val imageTopLeft = imageCornerObj?.get("topLeft")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageTopRight = imageCornerObj?.get("topRight")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageBottomLeft = imageCornerObj?.get("bottomLeft")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageBottomRight = imageCornerObj?.get("bottomRight")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp

    val imageMarginObj = imageCircleObj
        ?.get("margin")
        ?.takeIf { it !is JsonNull }
        ?.jsonObject

    val imageMarginTop = imageMarginObj?.get("top")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageMarginBottom = imageMarginObj?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageMarginLeft = imageMarginObj?.get("left")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp
    val imageMarginRight = imageMarginObj?.get("right")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp

            // -------- crossButton --------
            // Was read four separate times: three run{} blocks at the AppStorys call
            // site plus `enabled` inside CardScratch. One traversal now.
            val crossObj = details
                ?.get("crossButton")
                ?.takeIf { it !is JsonNull }
                ?.jsonObject
            val crossColors = crossObj?.get("color")?.takeIf { it !is JsonNull }?.jsonObject
            val crossMargin = crossObj?.get("margin")?.takeIf { it !is JsonNull }?.jsonObject

            return ScratchCardConfig(
                cardHeight = cardHeight,
                overlayImage = overlayImage,
                haptics = haptics,
                hapticsEnabled = hapticsEnabled,
                bannerImage = bannerImage,
                offerTitle = offerTitle,
                titleFontSize = titleFontSize,
                offerTitleColor = offerTitleColor,
                offerTitleFontFamily = offerTitleFontFamily,
                offerTitleFontDecoration = offerTitleFontDecoration,
                offerTitleTextAlign = offerTitleTextAlign,
                offerTitleMarginTop = offerTitleMarginTop,
                offerTitleMarginBottom = offerTitleMarginBottom,
                offerTitleMarginLeft = offerTitleMarginLeft,
                offerTitleMarginRight = offerTitleMarginRight,
                offerSubtitle = offerSubtitle,
                subtitleFontSize = subtitleFontSize,
                offerSubtitleColor = offerSubtitleColor,
                offerSubtitleFontFamily = offerSubtitleFontFamily,
                offerSubtitleFontDecoration = offerSubtitleFontDecoration,
                offerSubtitleTextAlign = offerSubtitleTextAlign,
                offerSubtitleMarginTop = offerSubtitleMarginTop,
                offerSubtitleMarginBottom = offerSubtitleMarginBottom,
                offerSubtitleMarginLeft = offerSubtitleMarginLeft,
                offerSubtitleMarginRight = offerSubtitleMarginRight,
                onlyImage = onlyImage,
                rewardBgColor = rewardBgColor,
                couponCode = couponCode,
                couponBgColor = couponBgColor,
                couponBorderColor = couponBorderColor,
                couponBorderWidth = couponBorderWidth,
                couponAlignment = couponAlignment,
                couponCtaFullWidth = couponCtaFullWidth,
                couponCtaWidth = couponCtaWidth,
                couponHeight = couponHeight,
                couponTextColor = couponTextColor,
                couponFontSize = couponFontSize,
                couponFontFamily = couponFontFamily,
                couponFontDecoration = couponFontDecoration,
                couponTopLeft = couponTopLeft,
                couponTopRight = couponTopRight,
                couponBottomLeft = couponBottomLeft,
                couponBottomRight = couponBottomRight,
                couponMarginTop = couponMarginTop,
                couponMarginBottom = couponMarginBottom,
                couponMarginLeft = couponMarginLeft,
                couponMarginRight = couponMarginRight,
                customSoundEnabled = customSoundEnabled,
                ctaHeight = ctaHeight,
                ctaColor = ctaColor,
                ctaBorderColor = ctaBorderColor,
                ctaBorderWidth = ctaBorderWidth,
                ctaAlignment = ctaAlignment,
                ctaFullWidth = ctaFullWidth,
                ctaWidth = ctaWidth,
                ctaText = ctaText,
                ctaTextColor = ctaTextColor,
                ctaFontSize = ctaFontSize,
                ctaFontFamily = ctaFontFamily,
                ctaFontDecoration = ctaFontDecoration,
                topLeft = topLeft,
                topRight = topRight,
                bottomLeft = bottomLeft,
                bottomRight = bottomRight,
                ctaPaddingTop = ctaPaddingTop,
                ctaPaddingBottom = ctaPaddingBottom,
                ctaPaddingLeft = ctaPaddingLeft,
                ctaPaddingRight = ctaPaddingRight,
                termsAndConditionsHtml = termsAndConditionsHtml,
                crossEnabled = crossObj?.get("enabled")?.jsonPrimitive?.booleanOrNull ?: true,
                crossAlignment = crossObj?.get("alignment")?.jsonPrimitive?.contentOrNull ?: "center",
                crossMarginBottom = crossMargin?.get("bottom")?.jsonPrimitive?.intOrNull?.dp ?: 0.dp,
                crossFillColor = crossColors?.get("fill")?.jsonPrimitive?.contentOrNull,
                crossColor = crossColors?.get("cross")?.jsonPrimitive?.contentOrNull,
                crossStrokeColor = crossColors?.get("stroke")?.jsonPrimitive?.contentOrNull,
                crossMarginTop = crossMargin?.get("top")?.jsonPrimitive?.intOrNull,
                crossMarginEnd = crossMargin?.get("right")?.jsonPrimitive?.intOrNull,
                crossSize = crossObj?.get("size")?.jsonPrimitive?.intOrNull,
                crossImageUrl = crossObj?.get("image")?.jsonPrimitive?.contentOrNull,
                cardWidth = cardWidth,
                cardHeightDp = cardHeightDp,
                cornerRadius = cornerRadius,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                imageTopLeft = imageTopLeft,
                imageTopRight = imageTopRight,
                imageBottomLeft = imageBottomLeft,
                imageBottomRight = imageBottomRight,
                imageMarginTop = imageMarginTop,
                imageMarginBottom = imageMarginBottom,
                imageMarginLeft = imageMarginLeft,
                imageMarginRight = imageMarginRight
            )
        }
    }
}
