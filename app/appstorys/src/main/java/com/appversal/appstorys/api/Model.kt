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
    val account_id: String?
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
    @SerialName("trigger_event") val triggerEvent: String?,
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
data class TrackActionStories(
    val campaign_id: String?,
    val user_id: String?,
    val event_type: String?,
    val story_slide: String?
)

@Keep
@Serializable
data class TrackActionTooltips(
    val campaign_id: String?,
    val user_id: String?,
    val event_type: String?,
    val tooltip_id: String?
)

@Keep
@Serializable
data class ReelActionRequest(
    val user_id: String?,
    val event_type: String?,
    val reel_id: String?,
    val campaign_id: String?,
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
    val slides: List<StorySlide>?
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
    val order: Int?
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
    val colors: BannerColors?,
    val margin: BannerMargin?,
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
    val topMargin: String?,
    val leftMargin: String?,
    val rightMargin: String?,
    val bottomMargin: String?,
    val topLeftRadius: String?,
    val topRightRadius: String?,
    val bottomLeftRadius: String?,
    val bottomRightRadius: String?,
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
    val delayDisplay: Int?,
    val displayDelay: String?,
    val csatTitleColor: String?,
    val csatCtaTextColor: String?,
    val csatLowStarColor: String?,
    val csatHighStarColor: String?,
    val csatUnselectedStarColor: String?,
    val csatBackgroundColor: String?,
    val csatOptionBoxColour: String?,
    val csatOptionTextColour: String?,
    val csatOptionStrokeColor: String?,
    val csatCtaBackgroundColor: String?,
    val csatAdditionalTextColor: String?,
    val csatDescriptionTextColor: String?,
    val csatSelectedOptionTextColor: String?,
    val csatSelectedOptionStrokeColor: String?,
    val csatSelectedOptionBackgroundColor: String?,

    val fontSize: Int? = null,

    val csatTitleFontSize: Int?,
    val csatTitleFontDecoration: List<String>?,
    val csatTitleAlignment: String?,
    val csatTitleLineHeight: Float?,
    val csatTitleMargin: Margin?,

    val csatDescriptionFontSize: Int?,
    val csatDescriptionFontDecoration: List<String>?,
    val csatDescriptionAlignment: String?,
    val csatDescriptionLineHeight: Float?,
    val csatDescriptionMargin: Margin?,

    val csatFeedbackTitleText: String?,
    val csatFeedbackTitleTextColor: String?,
    val csatFeedbackTitleFontSize: Int?,
    val csatFeedbackTitleFontDecoration: List<String>?,
    val csatFeedbackTitleAlignment: String?,
    val csatFeedbackTitleLineHeight: Float?,
    val csatFeedbackTitleMargin: Margin?,

    val csatFeedbackOptionFontSize: Int?,
    val csatFeedbackOptionFontDecoration: List<String>?,
    val csatFeedbackOptionAlignment: String?,
    val csatFeedbackOptionMargin: Margin?,

    val csatAdditionalTextFontSize: Int?,
    val csatAdditionalTextFontDecoration: List<String>?,
    val csatAdditionalTextMargin: Margin?,

    val csatCtaFontSize: Int?,
    val csatCtaFontDecoration: List<String>?,
    val csatCtaBorderColor: String?,
    val csatCtaBorderWidth: Int?,
    val csatCtaBorderRadius: String?,
    val csatCtaDimensions: Dimensions?,
    val csatCtaFullWidth: Boolean?,
    val csatCtaMargin: Margin?,
    val csatCtaAlignment: String?,

    val csatBottomPadding: String?,
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
    val clickAction: String?,
    val deepLinkUrl: String?,
    val target: String?,
    val order: Int?,
    val styling: TooltipStyling?,
    @SerialName("_id") val id: String?
)

@Keep
@Serializable
data class TooltipStyling(
    val tooltipDimensions: TooltipDimensions?,
    val highlightRadius: String?,
    val highlightPadding: String?,
    val backgroudColor: String?,
    val enableBackdrop: Boolean?,
    val tooltipArrow: TooltipArrow?,
    val spacing: TooltipSpacing?,
    val closeButton: Boolean?
)

@Keep
@Serializable
data class TooltipDimensions(
    val height: String?,
    val width: String?,
    val cornerRadius: String?
)

@Keep
@Serializable
data class TooltipArrow(
    val arrowHeight: String?,
    val arrowWidth: String?
)

@Keep
@Serializable
data class TooltipSpacing(
    val padding: TooltipPadding?
)

@Keep
@Serializable
data class TooltipPadding(
    val paddingTop: Int?,
    val paddingRight: Int?,
    val paddingBottom: Int?,
    val paddingLeft: Int?
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
    val pipTopPadding: String?,
    val pipBottomPadding: String?,
    val expandablePip: String?,
    val videoSelection: String?,
    val soundToggle: SoundToggle?,
    val crossButton: BannerStyleConfig?,
    val expandControls: ExpandControls?
)

@Keep
@Serializable
data class BottomSheetDetails(
    @SerialName("_id") val id: String?,
    val campaign: String?,
    val name: String?,
    val elements: List<BottomSheetElement>?,
    val cornerRadius: String?,
    val enableCrossButton: String?,
    val triggerType: String?,
    val selectedEvent: String?,
) : CampaignDetails()

@Keep
@Serializable
data class BottomSheetElement(
    val type: String?,
    val alignment: String?,
    val order: Int?,
    val id: String?,

    // Image-specific
    val url: String? = null,
    val imageLink: String? = null,
    val overlayButton: Boolean? = null,


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
    val ctaBorderRadius: Int? = null,
    val ctaHeight: Int? = null,
    val ctaWidth: Int? = null,
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
    val paddingBottom: Int? = null
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
    val colour: String?,
    val decoration: List<String>?
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
    val content: ModalContent? = null,
    val styling: ModalStyling? = null,
    val screen: Int? = null,
    val name: String? = null
)

@Serializable
data class ModalContent(
    val chooseMediaType: ModalMedia?,
    val titleText: String?,
    val subtitleText: String?,
    val primaryCtaText: String?,
    val primaryCtaRedirection: ModalRedirection?,
    val secondaryCtaText: String?,
    val secondaryCtaRedirection: ModalRedirection?
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
    val uploadImage: ModalUploadImage? = null
)

@Serializable
data class ModalCrossButtonDefault(
    val color: BannerColors? = null,
    val spacing: ModalSpacing? = null,
    val crossButtonImage: String? = null
)

@Serializable
data class ModalUploadImage(
    val url: String? = null
)

@Serializable
data class ModalSpacing(
    val margin: ModalMargin? = null
)

@Serializable
data class ModalMargin(
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null,
    val left: Int? = null
)

@Serializable
data class ModalCta(
    val backgroundColor: String? = null,
    val borderColor: String? = null,
    val containerStyle: ModalCtaContainer? = null,
    val cornerRadius: ModalCtaCornerRadius? = null,
    val occupyFullWidth: String? = null,
    val spacing: ModalSpacing? = null,
    val textColor: String? = null,
    val textStyle: ModalTextStyle? = null
)

@Serializable
data class ModalCtaContainer(
    val alignment: String? = null,
    val borderWidth: Int? = null,
    val ctaWidth: Int? = null,
    val height: Int? = null
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

@Serializable
data class ModalTextStyling(
    val alignment: String? = null,
    val color: String? = null,
    val font: String? = null,
    val fontStyle: String? = null,
    val size: Int? = null
)

@Serializable
data class ModalAppearance(
    val dimension: ModalDimension? = null,
    val cornerRadius: ModalCornerRadius? = null,
    val backdrop: ModalBackdrop? = null,
    val enableBackdrop: Boolean? = null,
    val padding: ModalPadding? = null,
    val ctaDisplay: String? = null
)

@Serializable
data class ModalPadding(
    val top: Int? = null,
    val right: Int? = null,
    val bottom: Int? = null,
    val left: Int? = null
)

@Serializable
data class ModalBackdrop(
    val color: String? = null,
    val opacity: Int? = null
)

@Serializable
data class ModalDimension(
    val height: String? = null,
    val borderWidth: String? = null
)

@Serializable
data class ModalCornerRadius(
    val topLeft: String? = null,
    val topRight: String? = null,
    val bottomLeft: String? = null,
    val bottomRight: String? = null
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
    val margin: MuteUnmuteMargin?,
    val selectedStyle: String?
)

@Keep
@Serializable
data class UnmuteButtonConfig(
    val colors: BannerColors?,
    val margin: MuteUnmuteMargin?,
    val selectedStyle: String?
)

@Keep
@Serializable
data class MuteUnmuteMargin(
    val top: String?,
    val right: String?,
    val bottom: String?,
    val left: String?
)

@Keep
@Serializable
data class ExpandControls(
    val option: String?,
    val enabled: Boolean?,
    val maximise: ExpandButtonStyleConfig?,
    val minimise: ExpandButtonStyleConfig?
)

@Keep
@Serializable
data class ExpandButtonStyleConfig(
    val colors: BannerColors?,
    val margin: MuteUnmuteMargin?,
    val selectedStyle: String?
)

