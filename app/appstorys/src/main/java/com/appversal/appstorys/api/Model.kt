package com.appversal.appstorys.api

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class CampaignDetails

@Keep
@Serializable
data class ValidateAccountRequest(
    val app_id: String?,
    val account_id: String?,
    val user_id: String?
)

@Keep
@Serializable
data class ReconcileUserRequest(
    val anonymous_user_id: String,
    val identified_user_id: String
)

@Keep
@Serializable
data class CampaignVariant(
    val id: String,
    val v_id: String
)

@Keep
@Serializable
data class VariantCampaignDetails(
    val variants: JsonObject
) : CampaignDetails()

@Keep
@Serializable
data class EligibleCampaignsResponse(
    val eligibleCampaignList: List<String>?,
    val userId: String,
    val variants: List<CampaignVariant>? = null,
    val personalization_data: Map<String, String>? = null,
    val test_user: Boolean? = null
)

@Keep
@Serializable
data class IdentifyPositionsRequest(
    val screen_name: String?,
    val position_list: List<String>?
)

@Keep
@Serializable
data class ValidateAccountResponse(
    val access_token: String?
)

@Keep
@Serializable
data class WebSocketConnectionResponse(
    val ws: WebSocketConfig,
    val userID: String,
    val screen_capture_enabled: Boolean?,
)

@Keep
@Serializable
data class WebSocketConfig(
    val expires: Int,
    val sessionID: String,
    val token: String,
    val url: String,
)

@Keep
@Serializable
data class TrackUserWebSocketRequest(
    val user_id: String,
    val screenName: String? = null,
    val silentUpdate: Boolean? = null,
)

@Keep
@Serializable
data class UpdateUserPropertiesRequest(
    val user_id: String,
    val attributes: Map<String, JsonElement>
)

@Keep
@Serializable(with = CampaignResponseDeserializer::class)
data class CampaignResponse(
    val userId: String?,
    @SerialName("message_id") val messageId: String?,
    val campaigns: List<Campaign>?
)

@Keep
@Serializable(with = CampaignDeserializer::class)
data class Campaign(
    val id: String?,
    @SerialName("campaign_type") val campaignType: String?,
    val details: CampaignDetails?,
    val position: String?,
    val screen: String?,
    @SerialName("trigger_event") val triggerEvent: String?
)

@Keep
@Serializable
data class ReelStatusRequest(
    val user_id: String?,
    val action: String?,
    val reel: String?
)

@Keep
@Serializable
data class StoriesDetails(
    val groups: List<StoryGroup>?
) : CampaignDetails()

@Keep
@Serializable
data class StoryGroup(
    val id: String?,
    val name: String?,
    val thumbnail: String?,
    val ringColor: String?,
    val nameColor: String?,
    val order: Int?,
    val slides: List<StorySlide>?,
    val styling: StoryGroupStyling?
)

@Keep
@Serializable
data class StoryGroupStyling(
    // Global viewer controls styling
    val cornerRadius: CornerRadius?,
    val crossButton: StoryButtonConfig?,
    val name: StoryNameConfig?,
    val ringWidth: Int?,
    val share: StoryButtonConfig?,
    val size: Int?,
    val slideShowTime: Int?,
    val soundToggle: StorySoundToggleConfig?,

    // Per-state thumbnail styling
    val ringAndImageSpace: Int?,
    val storyGroupNotViewed: StoryGroupState?,
    val storyGroupViewed: StoryGroupState?
)

@Keep
@Serializable
data class StoryGroupState(
    val ringColor: String?,
    val fontColor: String?,
    val fontSize: Int?,
    val fontDecoration: List<String>?
)

@Keep
@Serializable
data class StoryButtonConfig(
    val colors: StoryButtonColors?,    // Legacy field (plural)
    val color: StoryButtonColors?,     // New backend format (singular)
    val margin: StoryButtonMargin?,
    val image: String? = null,         // New backend format for button image
    val enabled: Boolean? = null,      // Whether button is enabled
    val selectedStyle: String? = null, // Style identifier (e.g., "cross4", "share4")
    val size: Int? = null              // Button size
)

@Keep
@Serializable
data class StoryButtonColors(
    val cross: String?,
    val fill: String?,
    val stroke: String?
)

@Keep
@Serializable
data class StoryButtonMargin(
    val right: Int?,
    val top: Int?
)

@Keep
@Serializable
data class StoryNameConfig(
    val font: String?,
    val size: Int?
)

@Keep
@Serializable
data class StorySoundToggleConfig(
    val mute: StoryButtonConfig?,
    val unmute: StoryButtonConfig?,
    val defaultSound: String? = null,  // Default sound state: "yes" or "no"
    val enabled: Boolean? = null       // Whether sound toggle is enabled
)

@Keep
@Serializable
data class StorySlide(
    val id: String?,
    val parent: String?,
    val image: String?,
    val video: String?,
    val link: String?,
    @SerialName("button_text") val buttonText: String?,
    val order: Int?,
    val styling: StorySlideStyling?
)

@Keep
@Serializable
data class StorySlideStyling(
    // Legacy fields (for backward compatibility)
    val borderWidth: Int?,
    val ctaAlignment: String?,
    val ctaBackground: StoryCtaBackground?,
    val ctaHeight: Int?,
    val ctaMargins: StoryCtaMargins?,
    val ctaText: StoryCtaText?,
    val fullWidthCta: Boolean?,

    // New nested CTA structure from backend
    val cta: StoryCtaConfig? = null
)

@Keep
@Serializable
data class StoryCtaConfig(
    val container: StoryCtaContainer?,
    val cornerRadius: StoryCtaCornerRadius?,
    val margin: StoryCtaMargins?,
    val text: StoryCtaTextConfig?
)

@Keep
@Serializable
data class StoryCtaContainer(
    val alignment: String?,
    val backgroundColor: String?,
    val borderColor: String?,
    val borderWidth: Int?,
    val ctaFullWidth: Boolean?,
    val ctaWidth: Int?,
    val height: Int?
)

@Keep
@Serializable
data class StoryCtaCornerRadius(
    val topLeft: Int?,
    val topRight: Int?,
    val bottomLeft: Int?,
    val bottomRight: Int?
)

@Keep
@Serializable
data class StoryCtaTextConfig(
    val color: String?,
    val fontDecoration: List<String>?,
    val fontFamily: String?,
    val fontSize: Int?
)

@Keep
@Serializable
data class StoryCtaBackground(
    val backgroundColor: String?,
    val borderColor: String?
)

@Keep
@Serializable
data class StoryCtaMargins(
    val bottom: Int?,
    val left: Int?,
    val right: Int?,
    val top: Int?
)

@Keep
@Serializable
data class StoryCtaText(
    val fontColor: String?,
    val fontSize: Int?
)

@Keep
@Serializable
data class BannerDetails(
    val id: String?,
    val image: String?,
    val width: Int?,
    val height: Int?,
    val link: JsonElement?,
    val styling: BannerStyling?,
    val lottie_data: String?,
    val crossButtonImage: String? // Banner image
) : CampaignDetails()

@Keep
@Serializable
data class BannerStyling(
    val enableCloseButton: Boolean?,
    val marginLeft: Int?,
    val marginRight: Int?,
    val marginBottom: Int?,
    val topLeftRadius: String?,
    val topRightRadius: String?,
    val bottomLeftRadius: String?,
    val bottomRightRadius: String?,
    val crossButton: BannerStyleConfig?,
)

@Keep
@Serializable
data class BannerStyleConfig(
    val colors: BannerColors?,      // Legacy field (plural)
    val color: BannerColors?,       // New backend format (singular)
    val size: Int?,
    val margin: BannerMargin?,
    val enabled: Boolean? = null,
    val image: String? = null,      // New backend format for cross button image
    val option: String? = null,
    val selectedStyle: String? = null
)

@Keep
@Serializable
data class BannerColors(
    val cross: String?,
    val fill: String?,
    val stroke: String?
)

@Keep
@Serializable
data class BannerMargin(
    val top: Int?,
    val right: Int?,
    val bottom: Int?,
    val left: Int?
)

@Keep
@Serializable
data class WidgetDetails(
    val id: String?,
    val type: String?,
    val width: Int?,
    val height: Int?,
    @SerialName("widget_images") val widgetImages: List<WidgetImage>?,
    val campaign: String?,
    val screen: String?,
    val styling: WidgetStyling?
) : CampaignDetails()

@Keep
@Serializable
data class WidgetStyling(
    val topMargin: Int?,
    val leftMargin: Int?,
    val rightMargin: Int?,
    val bottomMargin: Int?,
    val topLeftRadius: Int?,
    val topRightRadius: Int?,
    val bottomLeftRadius: Int?,
    val bottomRightRadius: Int?,
)

@Keep
@Serializable
data class WidgetImage(
    val id: String?,
    val image: String?,
    val link: JsonElement?,
    val order: Int?,
    val lottie_data: String?,
)

@Keep
@Serializable
data class FloaterDetails(
    val id: String?,
    val image: String?,
    val width: Int?,
    val height: Int?,
    val link: String?,
    val position: String?,
    val campaign: String?,
    val styling: FloaterStyling?,
    val lottie_data: String?,
) : CampaignDetails()

@Keep
@Serializable
data class FloaterStyling(
    val topLeftRadius: String?,
    val topRightRadius: String?,
    val bottomLeftRadius: String?,
    val bottomRightRadius: String?,
    val floaterBottomPadding: String?,
    val floaterRightPadding: String?,
    val floaterLeftPadding: String?,
)

@Keep
@Serializable
data class CSATDetails(
    val id: String?,
    val title: String?,
    val height: Int?,
    val width: Int?,
    val styling: CSATStyling?,
    val thankyouImage: String?,
    val thankyouText: String?,
    val thankyouDescription: String?,
    val highStarText: String?,
    val lowStarText: String?,
    @SerialName("description_text") val descriptionText: String?,
    @SerialName("feedback_option") val feedbackOption: FeedbackOption?,
    val campaign: String?,
    val link: String?
) : CampaignDetails()

@Keep
@Serializable
data class FeedbackOption(
    val option1: String?,
    val option2: String?,
    val option3: String?,
    val option4: String?,
    val option5: String?,
    val option6: String?,
    val option7: String?,
    val option8: String?,
    val option9: String?,
    val option10: String?,
    ) {
    fun toList(): List<String> = listOf(
        option1 ?: "",
        option2 ?: "",
        option3 ?: "",
        option4 ?: "",
        option5 ?: "",
        option6 ?: "",
        option7 ?: "",
        option8 ?: "",
        option9 ?: "",
        option10 ?: "",
        ).filter { it.isNotBlank() }
}

@Keep
@Serializable
data class CSATStyling(

    val fontSize: Int? = null,
    val appearance: CsatAppearance?,
    val csatCrossButton: BannerStyleConfig? = null,
    @SerialName("crossButton")
    val crossButton: BannerStyleConfig? = null,
    val feedbackPage: CsatFeedbackPage?,
    val initialFeedback: CsatInitialFeedback?,
    val rating: CsatRating?,
    val thankyouPage: CsatThankyouPage?
)

@Keep
@Serializable
data class CsatEmojiConfig(
    val selected: CsatEmojiState?,
    val unselected: CsatEmojiState?,
    val values: List<String>?
)

@Keep
@Serializable
data class CsatEmojiState(
    val stylingContainer: CsatStylingContainer?
)

@Keep
@Serializable
data class CsatStylingContainer(
    val border: String?,
    val borderWidth: Int?,
    val fill: String?
)

@Keep
@Serializable
data class CsatNumberConfig(
    val high: CsatNumberState?,
    val low: CsatNumberState?,
    val unselected: CsatNumberState?,
    val stylingNumber: CsatNumberTextStyle?
)

@Keep
@Serializable
data class CsatNumberState(
    val stylingContainer: CsatStylingContainer?,
    val stylingNumber: CsatNumberTextStyle?
)

@Keep
@Serializable
data class CsatNumberTextStyle(
    val text: String?,
    val textSize: Int?
)

@Keep
@Serializable
data class CsatAppearance(
    val backgroundColor: String?,
    val borderRadius: Int?,
    val displayDelay: JsonElement?,
    val margin: Margin?,
    val padding: Margin?
)

@Keep
@Serializable
data class CsatFeedbackPage(
    val additionalComments: CsatAdditionalComments?,
    val options: CsatOptions?,
    val submitButton: CsatButton?
)

@Keep
@Serializable
data class CsatAdditionalComments(
    val colors: CsatColors?,
    val enabled: Boolean?,
    val textStyle: CsatTextStyle?
)

@Keep
@Serializable
data class CsatOptions(
    val nonSelectedOptions: CsatOptionStyle?,
    val selectedOptions: CsatOptionStyle?
)

@Keep
@Serializable
data class CsatOptionStyle(
    val colors: CsatColors?,
    val textStyle: CsatTextStyle?
)

@Keep
@Serializable
data class CsatButton(
    val colors: CsatColors? = null,
    val containerRadius: CornerRadius? = null,
    val containerStyle: CsatContainerStyle? = null,
    val fullWidth: Boolean? = null,
    val margin: Margin? = null,
    val padding: Margin? = null,
    val text: String? = null,
    val textStyle: CsatTextStyle? = null,
    // Nested cta structure from JSON
    val cta: CsatCtaConfig? = null,
    val enabled: Boolean? = null
)

@Keep
@Serializable
data class CsatCtaConfig(
    val container: CsatCtaContainer?,
    val cornerRadius: CornerRadius?,
    val margin: Margin?,
    val text: CsatCtaText?
)

@Keep
@Serializable
data class CsatCtaContainer(
    val alignment: String?,
    val backgroundColor: String?,
    val borderColor: String?,
    val borderWidth: Int?,
    val ctaFullWidth: Boolean?,
    val ctaWidth: Int?,
    val height: Int?
)

@Keep
@Serializable
data class CsatCtaText(
    val color: String?,
    val fontDecoration: List<String>?,
    val fontFamily: String?,
    val fontSize: Int?
)

@Keep
@Serializable
data class CsatContainerStyle(
    val alignment: String?,
    val borderWidth: Int?,
    val height: Int?,
    val width: Int?
)

@Keep
@Serializable
data class CsatInitialFeedback(
    val subtitle: CsatTextElement?,
    val title: CsatTextElement?
)

@Keep
@Serializable
data class CsatTextElement(
    val alignment: String? = null,
    val color: String? = null,
    val textStyle: CsatTextStyle? = null
)

@Keep
@Serializable
data class CsatRating(
    val displayText: String? = null,
    val high: CsatRatingStyle? = null,
    val low: CsatRatingStyle? = null,
    val unselected: CsatRatingStyle? = null,
    // Nested star structure from JSON
    val alignment: String? = null,
    val ratingType: String? = null,
    val star: CsatStarConfig? = null,
    val emoji: CsatEmojiConfig? = null,
    val number: CsatNumberConfig? = null,
    val highRatingSubtitle: String? = null,
    val highRatingTitle: String? = null,
    val lowRatingSubtitle: String? = null,
    val lowRatingTitle: String? = null
)

@Keep
@Serializable
data class CsatStarConfig(
    val high: CsatStarStyle?,
    val low: CsatStarStyle?,
    val unselected: CsatStarStyle?
)

@Keep
@Serializable
data class CsatStarStyle(
    val stylingContainer: CsatStylingStar?,
    val stylingStar: CsatStylingStar?
)

@Keep
@Serializable
data class CsatStylingStar(
    val background: String?,
    val border: String?,
    val borderWidth: Int?
)

@Keep
@Serializable
data class CsatRatingStyle(
    val background: String?,
    val border: String?,
    val borderWidth: Int?,
    val highRatingTitle: String?,
    val lowRatingTitle: String?,
    val highRatingSubtitle: String?,
    val lowRatingSubtitle: String?
)

@Keep
@Serializable
data class CsatThankyouPage(
    val doneButton: CsatButton?,
    val imageStyle: CsatImageStyle?,
    val subtitle: CsatTextElement?,
    val title: CsatTextElement?
)

@Keep
@Serializable
data class CsatImageStyle(
    val width: Int?,
    val height: Int?,
    val margin: Margin?,
    val padding: Margin?
)

@Keep
@Serializable
data class CsatColors(
    val background: String?,
    val border: String?,
    val text: String?
)

@Keep
@Serializable
data class CsatTextStyle(
    val alignment: String? = null,
    val font: String? = null,
    val size: Int? = null,
    // Additional fields from JSON structure
    val color: String? = null,
    val fontFamily: String? = null,
    val fontSize: Int? = null,
    val textAlign: String? = null,
    val fontDecoration: List<String>? = null
)

@Keep
@Serializable
data class Margin(
    val top: Int?,
    val bottom: Int?,
    val left: Int?,
    val right: Int?
)

@Keep
@Serializable
data class Dimensions(
    val height: Int?,
    val width: Int?
)

@Keep
@Serializable
data class CsatFeedbackPostRequest(
    val csat: String?,
    val user_id: String?,
    val rating: Int?,
    val feedback_option: String? = null,
    val additional_comments: String = ""
)

@Keep
@Serializable
data class ReelsDetails(
    val id: String?,
    val reels: List<Reel>?,
    val styling: ReelStyling?
) : CampaignDetails()

@Keep
@Serializable
data class Reel(
    val id: String?,
    @SerialName("button_text") val buttonText: String?,
    val order: Int?,
    @SerialName("description_text") val descriptionText: String?,
    val video: String?,
    val likes: Int?,
    val thumbnail: String?,
    val link: String?
)

@Keep
@Serializable
data class ReelStyling(
    val ctaBoxColor: String?,
    val cornerRadius: String?,
    val ctaTextColor: String?,
    val thumbnailWidth: String?,
    val likeButtonColor: String?,
    val thumbnailHeight: String?,
    val descriptionTextColor: String?
)

@Keep
@Serializable
data class TooltipsDetails(
    @SerialName("_id") val id: String?,
    val campaign: String?,
    val name: String?,
    val tooltips: List<Tooltip>?,
    @SerialName("created_at") val createdAt: String?
) : CampaignDetails()

@Keep
@Serializable
data class Tooltip(
    val type: String?,
    val url: String?,
    val link: String?,
    val enableBackdrop: Boolean?,
    val target: String?,
    val order: Int?,
    @SerialName("_id") val id: String?,
    val titleText: String?,
    val subtitleText: String?,
    val ctaText: String?,
    val styling: TooltipStyling?
)

@Keep
@Serializable
data class TooltipStyling(
    val appearance: TooltipAppearance?,
    val cta: TooltipCta?,
    val subTitle: TooltipText?,
    val title: TooltipText?
)

@Keep
@Serializable
data class TooltipCta(
    val borderRadius: CornerRadius? = null,
    val cornerRadius: CornerRadius? = null,
    val container: TooltipCtaContainer? = null,
    val margin: TooltipCtaMargin? = null,
    val text: TooltipCtaText? = null
)

@Keep
@Serializable
data class TooltipCtaContainer(
    val alignment: String? = null,
    val backgroundColor: String? = null,
    val borderColor: String? = null,
    @Serializable(with = NullableIntSerializer::class)
    val borderWidth: Int? = null,
    val ctaFullWidth: Boolean? = null,
    @Serializable(with = NullableIntSerializer::class)
    val ctaWidth: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val height: Int? = null
)

@Keep
@Serializable
data class TooltipCtaMargin(
    val bottom: Int? = null,
    val left: Int? = null,
    val right: Int? = null,
    val top: Int? = null
)

@Keep
@Serializable
data class TooltipCtaText(
    val color: String? = null,
    val fontSize: Int? = null,
    val fontFamily: String? = null,
    val fontDecoration: List<String>? = null
)

@Keep
@Serializable
data class TooltipText(
    val color: String?,
    val fontSize: Int?,
    val textAlign: String?,
    val margin: Margin?,
    val fontFamily: String?,
    val fontDecoration: List<String>?
)

@Keep
@Serializable
data class TooltipAppearance(
    val arrowStyle: TooltipArrow?,
    val backdropOpacity: Int?,
    val colors: TooltipColors?,
    val cornerRadius: CornerRadius?,
    val highlight: TooltipHighlight?,
    val imageDimensions: TooltipImageDimensions?,
    val padding: Margin?
)

@Keep
@Serializable
data class TooltipArrow(
    val height: Int?,
    val width: Int?
)

@Keep
@Serializable
data class TooltipColors(
    val arrow: String?,
    val backdrop: String?,
    val tooltip: String?
)

@Keep
@Serializable
data class TooltipHighlight(
    val padding: Int?,
    val radius: Int?
)

@Keep
@Serializable
data class TooltipImageDimensions(
    val height: Int?,
    val width: Int?
)

@Keep
@Serializable
data class PipDetails(
    val id: String?,
    val position: String?,
    val small_video: String?,
    val large_video: String?,
    val height: Int?,
    val width: Int?,
    val styling: PipStyling?,
    val link: String?,
    val campaign: String?,
    val button_text: String?,

    val crossButtonImage: String?,
    val muteImage: String?,
    val unmuteImage: String?,
    val maximiseImage: String?,
    val minimiseImage: String?,

) : CampaignDetails()

@Keep
@Serializable
data class PipStyling(
    val ctaWidth: String?,
    val fontSize: String?,
    val ctaHeight: String?,
    val isMovable: Boolean?,
    val marginTop: String?,
    val fontFamily: String?,
    val marginLeft: String?,
    val marginRight: String?,
    val cornerRadius: String?,
    val ctaFullWidth: Boolean?,
    val marginBottom: String?,
    val fontDecoration: List<String>?,
    val ctaButtonTextColor: String?,
    val ctaButtonBackgroundColor: String?,
    val pipTopPadding: JsonElement?,  // Can be Int or String
    val pipBottomPadding: JsonElement?,  // Can be Int or String
    val expandablePip: String?,
    val videoSelection: String?,
    val soundToggle: SoundToggle?,
    val crossButton: BannerStyleConfig?,
    val expandControls: ExpandControls?,
    val cta: PipCta?, // structured CTA object (new)
    val appearance: PipAppearance? // appearance object with dimensions and default sound
)

@Keep
@Serializable
data class PipAppearance(
    val defaultSound: String?,
    val pipHeight: String?,
    val pipWidth: String?
)

@Keep
@Serializable
data class PipCta(
    val cornerRadius: CornerRadius? = null,
    val container: PipCtaContainer? = null,
    val margin: PipCtaMargin? = null,
    val text: PipCtaText? = null
)

@Keep
@Serializable
data class PipCtaContainer(
    val alignment: String? = null,
    val backgroundColor: String? = null,
    val borderColor: String? = null,
    val borderWidth: JsonElement? = null,  // Can be Int or String
    val ctaFullWidth: Boolean? = null,
    val ctaWidth: Int? = null,
    val height: JsonElement? = null  // Can be Int or String
)

@Keep
@Serializable
data class PipCtaMargin(
    val bottom: Int? = null,
    val left: Int? = null,
    val right: Int? = null,
    val top: Int? = null
)

@Keep
@Serializable
data class PipCtaText(
    val color: String? = null,
    val fontFamily: String? = null,
    val fontSize: Int? = null,
    val fontWeight: String? = null,
    val fontStyle: String? = null,
    val textDecoration: List<String>? = null,
    val fontDecoration: List<String>? = null  // Alternative field name from backend
)

@Keep
@Serializable
data class BottomSheetDetails(
    @SerialName("_id") val id: String?,
    val campaign: String?,
    val name: String?,
    val type: String?,
    val bottomsheetType: String? = null,  // "basic", "imageOnly", etc.
    val created_at: String?,

    // Backdrop styling (root level)
    val backdropColor: String?,
    val backdropOpacity: JsonElement? = null,  // Can be Int or String from backend

    // Container styling (root level)
    val backgroundColor: String?,

    // Cross button configuration (root level - new backend format)
    val crossButton: BannerStyleConfig?,

    // Elements
    val elements: List<BottomSheetElement>?,
    val cornerRadius: CornerRadius? = null,  // Can be an object with topLeft, topRight, etc.

    // Legacy fields
    val enableCrossButton: String?,
    val styling: BottomSheetStyling?,
    val triggerType: String?,
    val selectedEvent: String?,
) : CampaignDetails()

@Keep
@Serializable
data class BottomSheetStyling(
    val crossButton: BannerStyleConfig?,
    val backgroundColor: String?,
)

@Keep
@Serializable
data class BottomSheetCta(
    val container: BottomSheetCtaContainer?,
    val cornerRadius: CornerRadius?,
    val margin: BottomSheetCtaMargin?,
    val text: BottomSheetCtaText?
)

@Keep
@Serializable
data class BottomSheetCtaContainer(
    val alignment: String?,
    val backgroundColor: String?,
    val borderColor: String?,
    val borderWidth: JsonElement? = null,  // Can be Int or empty string
    val ctaBoxColor: String?,
    val ctaFullWidth: Boolean?,
    val ctaWidth: JsonElement? = null,  // Can be Int or empty string
    val height: Int?
)

@Keep
@Serializable
data class BottomSheetCtaMargin(
    val bottom: Int?,
    val left: Int?,
    val right: Int?,
    val top: Int?
)

@Keep
@Serializable
data class BottomSheetCtaText(
    val color: String?,
    val fontDecoration: List<String>?,
    val fontFamily: String?,
    val fontSize: Int?
)

@Keep
@Serializable
data class BottomSheetElement(
    val type: String?,
    val bottomsheetType: String?, // Backend field for element type (e.g., "imageOnly")
    val alignment: String?,
    val order: Int?,
    val id: String?,

    // Image-specific
    val url: String? = null,
    val imageLink: String? = null,
    val imageBackgroundColor: String? = null,
    val overlayButton: Boolean? = null,
    val cornerRadius: CornerRadius? = null,

    // Body-specific
    val titleText: String? = null,
    val titleFontStyle: FontStyle? = null,
    val titleFontSize: Int? = null,
    val descriptionText: String? = null,
    val descriptionFontStyle: FontStyle? = null,
    val descriptionFontSize: Int? = null,
    val titleLineHeight: Float? = null,
    val descriptionLineHeight: Float? = null,
    val spacingBetweenTitleDesc: Float? = null,
    val bodyBackgroundColor: String? = null,

    // CTA-specific
    val ctaText: String? = null,
    val ctaLink: String? = null,
    val position: String? = null,

    // New nested CTA structure from backend
    val cta: BottomSheetCta? = null,

    // Legacy flat CTA fields (still supported)
    val ctaBorderRadius: CornerRadius?,
    val ctaHeight: JsonElement? = null,
    val ctaWidth: JsonElement? = null,
    val ctaTextColour: String? = null,
    val ctaFontSize: String? = null,
    val ctaFontFamily: String? = null,
    val ctaFontDecoration: List<String>? = emptyList(),
    val ctaBoxColor: String? = null,
    val ctaBackgroundColor: String? = null,
    val ctaFullWidth: Boolean? = null,

    // Shared paddings
    val paddingLeft: Int? = null,
    val paddingRight: Int? = null,
    val paddingTop: Int? = null,
    val paddingBottom: Int? = null,

    // Shared paddings
    val marginLeft: Int? = null,
    val marginRight: Int? = null,
    val marginTop: Int? = null,
    val marginBottom: Int? = null
)

@Keep
@Serializable
data class SurveyDetails(
    val id: String?,
    val name: String?,
    val styling: SurveyStyling?,
    val surveyQuestion: String?,
    val surveyOptions: Map<String, String>?,
    val campaign: String?,
    val hasOthers: Boolean?
) : CampaignDetails()

@Keep
@Serializable
data class SurveyStyling(
    val optionColor: String?,
    val displayDelay: String?,
    val backgroundColor: String?,
    val optionTextColor: String?,
    val othersTextColor: String?,
    val surveyTextColor: String?,
    val ctaTextIconColor: String?,
    val ctaBackgroundColor: String?,
    val selectedOptionColor: String?,
    val surveyQuestionColor: String?,
    val othersBackgroundColor: String?,
    val selectedOptionTextColor: String?
)

@Keep
@Serializable
data class SurveyFeedbackPostRequest(
    val user_id: String?,
    val survey: String?,
    val responseOptions: List<String>? = null,
    val comment: String? = ""
)

@Keep
@Serializable
data class FontStyle(
    val fontFamily: String?,
    val fontSize: JsonElement? = null,  // Can be String or Int from backend
    val colour: String?,
    val decoration: List<String>?,
    val alignment: String?  // Text alignment (center, left, right)
)



@Keep
@Serializable
data class ModalDetails(
    val id: String?,
    val modals: List<Modal>?,
    val name: String? = null
) : CampaignDetails()


@Keep
@Serializable
data class Modal(
    @SerialName("id") val id: String?,
    @SerialName("modal_type") val modalType: String? = null,

    // ---------- CONTENT-BASED (CTA / CAROUSEL) ----------
    val content: ModalContent? = null,
    val styling: ModalStyling? = null,
    val screen: Int? = null,
    val name: String? = null,

    // ---------- FLAT MEDIA-ONLY MODAL ----------
    val chooseMediaType: ModalMedia? = null,
    val link: String? = null,
    val url: String? = null,
    val size: String? = null,
    val backgroundOpacity: String? = null,
    val borderRadius: Int? = null,
    val enableBackdrop: Boolean? = null,
    val enableCrossButton: Boolean? = null,
    val crossButtonImage: String? = null,
    val redirection: ModalRedirection? = null
)


@Serializable
data class ModalContent(
    val chooseMediaType: ModalMedia?,
    val titleText: String?,
    val subtitleText: String?,
    val primaryCtaText: String?,
    val primaryCtaRedirection: ModalRedirection?,
    val secondaryCtaText: String?,
    val secondaryCtaRedirection: ModalRedirection?,
    // Support an optional `set` of slides (some backends return `content.set` for carousel modals).
    // The slide objects use the same shape as ModalContent so we reuse the type recursively.
    val set: List<ModalContent>? = null,
    // Per-slide styling/overrides (optional). Some payloads include `styling` inside each slide.
    val styling: ModalStyling? = null,
    // Alternate/backward-compatible CTA keys that some backends use
    @SerialName("primaryCta") val primaryCta: String? = null,
    @SerialName("secondayCta") val secondayCta: String? = null,
    @SerialName("secondaryCta") val secondaryCtaAlt: String? = null,
    // Accept content-level enable flag (some payloads put this under content)
    @SerialName("enableCrossButton") val enableCrossButton: String? = null
)

@Serializable
data class ModalMedia(
    val type: String?, // image | gif | lottie
    val url: String?
)

@Serializable
data class ModalRedirection(
    val type: String?, // url | deeplink
    val url: String?,
    val value: String?,
    val key: String? = null,
    val pageName: String? = null
)

@Serializable
data class ModalStyling(
    val appearance: ModalAppearance?,
    val crossButton: ModalCrossButton? = null,
    val primaryCta: ModalCta? = null,
    val secondaryCta: ModalCta? = null,
    val title: ModalTextStyling? = null,
    val subTitle: ModalTextStyling? = null
)

@Serializable
data class ModalCrossButton(
    val default: ModalCrossButtonDefault? = null,
    val enableCrossButton: Boolean? = null,
    val uploadImage: ModalUploadImage? = null,
    // Alternative structure support (for legacy/image-only modals)
    val color: BannerColors? = null,  // Some payloads use "color" directly
    val colors: BannerColors? = null, // Some payloads use "colors"
    val enabled: Boolean? = null,
    val image: String? = null,        // Some payloads use "image" for cross button image
    val margin: ModalMargin? = null,
    val option: String? = null,
    val selectedStyle: String? = null,
    @Serializable(with = NullableIntSerializer::class)
    val size: Int? = null,
)

@Serializable
data class ModalCrossButtonDefault(
    val color: BannerColors? = null,
    val spacing: ModalSpacing? = null,
    val crossButtonImage: String? = null,
    val size: Int? = null
)

@Serializable
data class ModalUploadImage(
    val url: String? = null
)

@Serializable
data class ModalSpacing(
    val margin: ModalMargin? = null,
    val padding: ModalPadding? = null
)

@Serializable
data class ModalMargin(
    @Serializable(with = NullableIntSerializer::class)
    val top: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val right: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val bottom: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val left: Int? = null
)

// Add ModalPadding definition (same shape as ModalMargin) because some payloads use `padding` inside ModalAppearance
@Serializable
data class ModalPadding(
    @Serializable(with = NullableIntSerializer::class)
    val top: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val right: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val bottom: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val left: Int? = null
)

@Serializable
data class ModalCta(
    val backgroundColor: String? = null,
    val borderColor: String? = null,
    val containerStyle: ModalCtaContainer? = null,
    // Alternate field name used by some backends
    val container: ModalCtaContainer? = null,
    val cornerRadius: ModalCtaCornerRadius? = null,
    val occupyFullWidth: String? = null,
    val spacing: ModalSpacing? = null,
    // Alternate: some backends send margin directly instead of spacing.margin
    val margin: ModalMargin? = null,
    val textColor: String? = null,
    val textStyle: ModalTextStyle? = null,
    // Alternate field name used by some backends
    val text: ModalCtaText? = null
)

@Serializable
data class ModalCtaContainer(
    val alignment: String? = null,
    val borderWidth: Int? = null,
    val ctaWidth: Int? = null,
    val height: Int? = null,
    // Additional fields that some backends put inside container
    val backgroundColor: String? = null,
    val borderColor: String? = null,
    val ctaFullWidth: Boolean? = null
)

@Serializable
data class ModalCtaCornerRadius(
    val topLeft: Int? = null,
    val topRight: Int? = null,
    val bottomLeft: Int? = null,
    val bottomRight: Int? = null
)

@Serializable
data class ModalTextStyle(
    val font: String? = null,
    val size: Int? = null
)

// Alternate text styling format used by some backends (inside "text" key)
@Serializable
data class ModalCtaText(
    val color: String? = null,
    val fontSize: Int? = null,
    val fontFamily: String? = null,
    val fontDecoration: List<String>? = null
)

@Serializable
data class ModalTextStyling(
    val alignment: String? = null,
    val textAlign: String? = null,  // Alternate field name
    val color: String? = null,
    val font: String? = null,
    val fontFamily: String? = null,  // Alternate field name
    val fontStyle: String? = null,
    val size: Int? = null,
    val fontSize: Int? = null,  // Alternate field name
    val fontDecoration: List<String>? = null  // Support bold, italic, underline
)

@Serializable
data class ModalAppearance(
    val dimension: ModalDimension? = null,
    val cornerRadius: ModalCornerRadius? = null,
    val backdrop: ModalBackdrop? = null,
    val enableBackdrop: Boolean? = null,
    val padding: ModalPadding? = null,
    val ctaDisplay: String? = null,
    // Background color for modal content area
    val backgroundColor: String? = null,
    // Accept alternate flat keys used by some backends
    val backdropColor: String? = null,
    val backdropOpacity: String? = null,
)

@Serializable
data class ModalBackdrop(
    val color: String? = null,
    // keep as String? to accept numeric or string representations from backend
    val opacity: String? = null
)

@Serializable
data class ModalDimension(
    val height: String? = null,
    val borderWidth: String? = null
)

@Serializable
data class ModalCornerRadius(
    @Serializable(with = NullableIntSerializer::class)
    val topLeft: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val topRight: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val bottomLeft: Int? = null,
    @Serializable(with = NullableIntSerializer::class)
    val bottomRight: Int? = null
)

@Keep
@Serializable
data class ScratchCardDetails(
    val id: String?,
    val bannerImage: String?,
    val coverImage: String?,
    val height: Int?,
    val width: Int?,
    val soundFile: String?,
    val content: JsonObject? = null,
    val styling: JsonObject? = null
) : CampaignDetails()

@Keep
@Serializable
data class MilestoneDetails(
    val id: String?,
    val content: MilestoneContent?,
    @SerialName("milestone_items") val milestoneItems: List<MilestoneItem>?,
    val styling: MilestoneStyling?
) : CampaignDetails()

@Keep
@Serializable
data class MilestoneContent(
    val showStreaksAs: String?, // "banner" or "modals"
    val totalStepCount: Int?
)

@Keep
@Serializable
data class MilestoneItem(
    val id: String?,
    val image: String?,
    val order: Int?,
    val triggerEvents: List<MilestoneTriggerEvent>?
)

@Keep
@Serializable
data class MilestoneTriggerEvent(
    val eventName: String?
)

@Keep
@Serializable
data class MilestoneStyling(
    val banner: MilestoneBannerStyling?
)

@Keep
@Serializable
data class MilestoneBannerStyling(
    val marginTop: String?,
    val marginBottom: String?,
    val marginLeft: String?,
    val marginRight: String?,
    val borderRadiusTopLeft: String?,
    val borderRadiusTopRight: String?,
    val borderRadiusBottomLeft: String?,
    val borderRadiusBottomRight: String?
)

@Keep
@Serializable
data class CornerRadius(
    val bottomLeft: Int?,
    val bottomRight: Int?,
    val topLeft: Int?,
    val topRight: Int?
)

@Keep
@Serializable
data class SoundToggle(
    val defaultSound: String?,
    val enabled: Boolean?,
    val option: String?,
    val mute: MuteButtonConfig?,
    val unmute: UnmuteButtonConfig?
)

@Keep
@Serializable
data class MuteButtonConfig(
    val colors: BannerColors?,
    val color: BannerColors?,
    val size: Int?,
    val margin: MuteUnmuteMargin?,
    val selectedStyle: String?,
    val image: String?
)

@Keep
@Serializable
data class UnmuteButtonConfig(
    val colors: BannerColors?,
    val color: BannerColors?,
    val size: Int?,
    val margin: MuteUnmuteMargin?,
    val selectedStyle: String?,
    val image: String?
)

@Keep
@Serializable
data class MuteUnmuteMargin(
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null,
    val left: Int? = null
)

@Keep
@Serializable
data class ExpandControls(
    val option: String?,
    val enabled: Boolean?,
    val maximise: MaximiseButtonStyleConfig?,
    val minimise: MinimiseButtonStyleConfig?
)


//Modal files
@Keep
@Serializable
data class MaximiseButtonStyleConfig(
    val colors: BannerColors?,
    val color: BannerColors?,
    val size: Int?,
    val margin: MuteUnmuteMargin?,
    val selectedStyle: String?,
    val image: String?
)

@Keep
@Serializable
data class MinimiseButtonStyleConfig(
    val colors: BannerColors?,
    val color: BannerColors?,
    val size: Int?,
    val margin: MuteUnmuteMargin?,
    val selectedStyle: String?,
    val image: String?
)

// Common Components Start
@Keep
@Serializable
data class TextStyling(
    val color: String?,
    val fontFamily: String?,
    val fontSize: Int?,
    val margin: CommonMargins? = null,
    val textAlign: String? = null,
    val fontDecoration: List<String>? = null
)

@Keep
@Serializable
data class CommonMargins(
    val bottom: Int?,
    val left: Int?,
    val right: Int?,
    val top: Int?
)
// Common Components End

