package com.appversal.appstorys.api

import android.util.Log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

object CampaignDetailsSerializer : KSerializer<CampaignDetails?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CampaignDetails", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: CampaignDetails?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }

        val jsonEncoder = encoder as? JsonEncoder ?: error("Only JSON format is supported")
        val element = when (value) {

            is VariantCampaignDetails -> jsonEncoder.json.encodeToJsonElement(
                VariantCampaignDetails.serializer(),
                value
            )

            is FloaterDetails -> jsonEncoder.json.encodeToJsonElement(
                FloaterDetails.serializer(),
                value
            )

            is CSATDetails -> jsonEncoder.json.encodeToJsonElement(CSATDetails.serializer(), value)
            is WidgetDetails -> jsonEncoder.json.encodeToJsonElement(
                WidgetDetails.serializer(),
                value
            )

            is BannerDetails -> jsonEncoder.json.encodeToJsonElement(
                BannerDetails.serializer(),
                value
            )

            is ReelsDetails -> jsonEncoder.json.encodeToJsonElement(
                ReelsDetails.serializer(),
                value
            )

            is TooltipsDetails -> jsonEncoder.json.encodeToJsonElement(
                TooltipsDetails.serializer(),
                value
            )

            is PipDetails -> jsonEncoder.json.encodeToJsonElement(PipDetails.serializer(), value)
            is BottomSheetDetails -> jsonEncoder.json.encodeToJsonElement(
                BottomSheetDetails.serializer(),
                value
            )

            is SurveyDetails -> jsonEncoder.json.encodeToJsonElement(
                SurveyDetails.serializer(),
                value
            )

            is ModalDetails -> jsonEncoder.json.encodeToJsonElement(
                ModalDetails.serializer(),
                value
            )

            is StoriesDetails -> jsonEncoder.json.encodeToJsonElement(
                serializer<List<StoryGroup>>(),
                value.groups.orEmpty()
            )

            is ScratchCardDetails -> jsonEncoder.json.encodeToJsonElement(
                ScratchCardDetails.serializer(),
                value
            )

            is MilestoneDetails -> jsonEncoder.json.encodeToJsonElement(
                MilestoneDetails.serializer(),
                value
            )

        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): CampaignDetails? {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Only JSON format is supported")
        val element = jsonDecoder.decodeJsonElement()

        if (element is JsonNull || element == JsonNull) {
            return null
        }

        if (element !is JsonObject) {
            Log.e("CampaignDetailsSer", "Expected JsonObject but got: ${element::class.simpleName}")
            return null
        }

        // This will be called by the custom Campaign deserializer which has access to campaign_type
        return null
    }
}

object CampaignDeserializer : KSerializer<Campaign> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Campaign", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Campaign) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Only JSON format is supported")
        val element = buildJsonObject {
            value.id?.let { put("id", it) }
            value.campaignType?.let { put("campaign_type", it) }
            value.position?.let { put("position", it) }
            value.screen?.let { put("screen", it) }
            value.triggerEvent?.let { put("trigger_event", it) }
            // details will be handled separately
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Campaign {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Only JSON format is supported")
        val element = jsonDecoder.decodeJsonElement().jsonObject

        val id = element["id"]?.jsonPrimitive?.contentOrNull
        val campaignType = element["campaign_type"]?.jsonPrimitive?.contentOrNull ?: ""
        val position = element["position"]?.jsonPrimitive?.contentOrNull
        val screen = element["screen"]?.jsonPrimitive?.contentOrNull ?: ""
        val triggerEvent = element["trigger_event"]?.jsonPrimitive?.contentOrNull

        val detailsElement = element["details"]
        val details: CampaignDetails? = if (detailsElement != null && detailsElement !is JsonNull) {
            try {
                // Check if details contains a "variants" key
                if (detailsElement is JsonObject && detailsElement.containsKey("variants")) {
                    Log.d("CampaignDeserializer", "Campaign $id has variants, storing as VariantCampaignDetails")
                    jsonDecoder.json.decodeFromJsonElement(
                        serializer<VariantCampaignDetails>(),
                        detailsElement
                    )
                } else {

                    when (campaignType) {
                        "FLT" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<FloaterDetails>(),
                            detailsElement
                        )

                        "CSAT" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<CSATDetails>(),
                            detailsElement
                        )

                        "WID" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<WidgetDetails>(),
                            detailsElement
                        )

                        "BAN" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<BannerDetails>(),
                            detailsElement
                        )

                        "REL" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<ReelsDetails>(),
                            detailsElement
                        )

                        "TTP" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<TooltipsDetails>(),
                            detailsElement
                        )

                        "PIP" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<PipDetails>(),
                            detailsElement
                        )

                        "BTS" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<BottomSheetDetails>(),
                            detailsElement
                        )

                        "SUR" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<SurveyDetails>(),
                            detailsElement
                        )

                        "MOD" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<ModalDetails>(),
                            detailsElement
                        )

                        "STR" -> StoriesDetails(
                            jsonDecoder.json.decodeFromJsonElement(
                                serializer<List<StoryGroup>>(),
                                detailsElement
                            )
                        )

                        "SCRT" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<ScratchCardDetails>(),
                            detailsElement
                        )

                        "MIL" -> jsonDecoder.json.decodeFromJsonElement(
                            serializer<MilestoneDetails>(),
                            detailsElement
                        )

                        else -> {
                            Log.e("CampaignDeserializer", "Unknown campaign type: $campaignType")
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    "CampaignDeserializer",
                    "Error deserializing campaign details for type $campaignType: ${e.message}",
                    e
                )
                null
            }
        } else {
            null
        }

        return Campaign(
            id = id,
            campaignType = campaignType,
            details = details,
            position = position,
            screen = screen,
            triggerEvent = triggerEvent
        )
    }
}

object CampaignResponseDeserializer : KSerializer<CampaignResponse> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CampaignResponse", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CampaignResponse) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Only JSON format is supported")
        val element = buildJsonObject {
            value.userId?.let { put("userId", it) }
            value.messageId?.let { put("message_id", it) }
            value.campaigns?.let { campaigns ->
                put("campaigns", buildJsonArray {
                    campaigns.forEach { campaign ->
                        add(jsonEncoder.json.encodeToJsonElement(CampaignDeserializer, campaign))
                    }
                })
            }
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): CampaignResponse {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Only JSON format is supported")
        val element = jsonDecoder.decodeJsonElement().jsonObject

        val userId = element["userId"]?.jsonPrimitive?.contentOrNull
            ?: element["user_id"]?.jsonPrimitive?.contentOrNull
        val messageId = element["message_id"]?.jsonPrimitive?.contentOrNull

        val campaignsArray = element["campaigns"]?.jsonArray
        val campaigns = campaignsArray?.map { campaignElement ->
            jsonDecoder.json.decodeFromJsonElement(CampaignDeserializer, campaignElement)
        }

        return CampaignResponse(
            userId = userId,
            messageId = messageId,
            campaigns = campaigns
        )
    }
}

    /**
     * Custom serializer for nullable Int that treats empty strings as null.
     * Backend sometimes sends empty strings ("") instead of omitting the field or sending null.
     */
    object NullableIntSerializer : KSerializer<Int?> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("NullableInt", PrimitiveKind.INT)

        override fun deserialize(decoder: Decoder): Int? {
            val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeInt()
            val element = jsonDecoder.decodeJsonElement()

            // Handle empty string as null
            if (element is kotlinx.serialization.json.JsonPrimitive) {
                val content = element.contentOrNull
                if (content.isNullOrEmpty()) return null
                return content.toIntOrNull()
            }

            return null
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun serialize(encoder: Encoder, value: Int?) {
            if (value == null) {
                encoder.encodeNull()
            } else {
                encoder.encodeInt(value)
            }
        }
    }

