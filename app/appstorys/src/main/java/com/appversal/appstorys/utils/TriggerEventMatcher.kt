package com.appversal.appstorys.utils

import com.appversal.appstorys.api.TriggerEvent
import com.appversal.appstorys.api.TriggerEventConfig

/**
 * Helper object for trigger event matching logic
 */
object TriggerEventMatcher {

    /**
     * Data class to store tracked event with its metadata
     */
    data class TrackedEventData(
        val eventName: String,
        val metadata: Map<String, Any>? = null
    )

    /**
     * Check if a trigger event matches any of the tracked events
     *
     * @param triggerEvent The trigger event from the campaign (can be null, string, or object)
     * @param campaignId The campaign ID for viaAppStorys handling
     * @param trackedEvents Set of tracked event data
     * @return true if the campaign should be shown, false otherwise
     */
    fun shouldShowCampaign(
        triggerEvent: TriggerEvent?,
        campaignId: String?,
        trackedEvents: Set<TrackedEventData>
    ): Boolean {
        // If no trigger event, always show
        if (triggerEvent == null) return true

        return when (triggerEvent) {
            is TriggerEvent.StringTrigger -> {
                if (triggerEvent.event.isEmpty()) return true
                // Handle viaAppStorys special case
                val eventToMatch = if (triggerEvent.event == "viaAppStorys") {
                    "viaAppStorys${campaignId}"
                } else {
                    triggerEvent.event
                }

                // Check if any tracked event matches the event name
                trackedEvents.any { it.eventName == eventToMatch }
            }

            is TriggerEvent.ObjectTrigger -> {
                if (triggerEvent.event.isEmpty()) return true
                // Find matching event by name first
                val matchingEvents = trackedEvents.filter { it.eventName == triggerEvent.event }

                // For each matching event, check if all conditions are satisfied
                matchingEvents.any { trackedEvent ->
                    matchesAllConditions(triggerEvent.eventConfig, trackedEvent.metadata)
                }
            }
        }
    }

    /**
     * Check if all trigger event conditions are satisfied by the metadata
     *
     * @param conditions List of trigger event conditions
     * @param metadata The metadata from the tracked event
     * @return true if all conditions are satisfied, false otherwise
     */
    private fun matchesAllConditions(
        conditions: List<TriggerEventConfig>,
        metadata: Map<String, Any>?
    ): Boolean {
        // If no metadata but we have conditions, fail
        if (metadata == null && conditions.isNotEmpty()) return false
        if (metadata == null) return true

        // All conditions must be satisfied
        return conditions.all { condition ->
            matchesCondition(condition, metadata)
        }
    }

    /**
     * Check if a single condition is satisfied
     *
     * @param condition The trigger event condition
     * @param metadata The metadata from the tracked event
     * @return true if the condition is satisfied, false otherwise
     */
    private fun matchesCondition(
        condition: TriggerEventConfig,
        metadata: Map<String, Any>
    ): Boolean {
        val metadataValue = metadata[condition.key] ?: return false
        val metadataValueStr = metadataValue.toString()
        val conditionValue = condition.value

        return when (condition.operator) {
            "eq" -> metadataValueStr == conditionValue
            "neq" -> metadataValueStr != conditionValue
            "gt" -> compareNumeric(metadataValueStr, conditionValue) { a, b -> a > b }
            "gte" -> compareNumeric(metadataValueStr, conditionValue) { a, b -> a >= b }
            "lt" -> compareNumeric(metadataValueStr, conditionValue) { a, b -> a < b }
            "lte" -> compareNumeric(metadataValueStr, conditionValue) { a, b -> a <= b }
            else -> {
                // Unknown operator, fail the condition
                false
            }
        }
    }

    /**
     * Compare two values numerically
     * Tries to parse as Double first, falls back to string comparison if parsing fails
     *
     * @param value1 First value as string
     * @param value2 Second value as string
     * @param comparison Lambda to perform the comparison
     * @return true if the comparison is satisfied, false otherwise
     */
    private fun compareNumeric(
        value1: String,
        value2: String,
        comparison: (Double, Double) -> Boolean
    ): Boolean {
        return try {
            val num1 = value1.toDouble()
            val num2 = value2.toDouble()
            comparison(num1, num2)
        } catch (e: NumberFormatException) {
            // If not numeric, fall back to string comparison
            comparison(value1.compareTo(value2).toDouble(), 0.0)
        }
    }
}