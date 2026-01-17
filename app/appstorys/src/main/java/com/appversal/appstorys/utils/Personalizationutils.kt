package com.appversal.appstorys.utils

import com.appversal.appstorys.AppStorys

fun personalizeText(
        text: String
    ): String {
        if (text.isBlank()) return text

        if (AppStorys.getPersonalizationData().isEmpty()) {
            // If no personalization data, return text with fallback values
            return replacePlaceholdersWithFallback(text)
        }

        // Regex to match {{variable | fallback}}
        val regex = """\{\{([^|}\s]+)\s*\|\s*([^}]+)\}\}""".toRegex()

        return regex.replace(text) { matchResult ->
            val variableName = matchResult.groupValues[1].trim()
            val fallbackValue = matchResult.groupValues[2].trim()

            AppStorys.getPersonalizationData()[variableName] ?: fallbackValue
        }
    }

    private fun replacePlaceholdersWithFallback(text: String): String {
        val regex = """\{\{[^|}\s]+\s*\|\s*([^}]+)\}\}""".toRegex()
        return regex.replace(text) { matchResult ->
            matchResult.groupValues[1].trim()
        }
    }
