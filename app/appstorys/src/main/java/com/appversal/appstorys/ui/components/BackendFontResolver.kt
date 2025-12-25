package com.appversal.appstorys.ui.typography

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.appversal.appstorys.R

// Simple in-memory cache shared across the process to avoid reloading fonts repeatedly
private val fontFamilyCache: MutableMap<String, FontFamily> = mutableMapOf()

@Composable
fun rememberBackendFontFamily(
    fontFamilyName: String?,
    weight: FontWeight,
    style: FontStyle
): FontFamily {
    return remember(fontFamilyName, weight, style) {
        if (fontFamilyName.isNullOrBlank()) {
            Log.d("BackendFontResolver", "fontFamilyName is blank -> using FontFamily.Default")
            FontFamily.Default
        } else {
            try {
                val nameTrim = fontFamilyName.trim()
                val nameLower = nameTrim.lowercase()
                val cacheKey = "$nameLower|$weight|$style"

                // Check cache first (synchronized for safety)
                synchronized(fontFamilyCache) {
                    fontFamilyCache[cacheKey]?.let {
                        Log.d("BackendFontResolver", "Cache hit for '$nameTrim' -> $it")
                        return@remember it
                    }
                }

                // Prefer system families for common desktop fonts for reliability
                when {
                    nameLower.contains("arial") || nameLower.contains("sans") || nameLower.contains("helvetica") -> {
                        Log.d("BackendFontResolver", "Mapping '$nameTrim' to FontFamily.SansSerif (system)")
                        val resolved = FontFamily.SansSerif
                        synchronized(fontFamilyCache) { fontFamilyCache[cacheKey] = resolved }
                        return@remember resolved
                    }
                    nameLower.contains("times") || nameLower.contains("serif") -> {
                        Log.d("BackendFontResolver", "Mapping '$nameTrim' to FontFamily.Serif (system)")
                        val resolved = FontFamily.Serif
                        synchronized(fontFamilyCache) { fontFamilyCache[cacheKey] = resolved }
                        return@remember resolved
                    }
                    nameLower.contains("mono") || nameLower.contains("courier") || nameLower.contains("monospace") -> {
                        Log.d("BackendFontResolver", "Mapping '$nameTrim' to FontFamily.Monospace (system)")
                        val resolved = FontFamily.Monospace
                        synchronized(fontFamilyCache) { fontFamilyCache[cacheKey] = resolved }
                        return@remember resolved
                    }
                    nameLower.contains("verdana") -> {
                        Log.d("BackendFontResolver", "Mapping 'Verdana' to FontFamily.SansSerif (system)")
                        val resolved = FontFamily.SansSerif
                        synchronized(fontFamilyCache) { fontFamilyCache[cacheKey] = resolved }
                        return@remember resolved
                    }
                }

                // Only try Google Fonts for names that are commonly available on Google Fonts
                val knownGoogleFont = when {
                    nameLower.contains("open sans") -> "Open Sans"
                    nameLower.contains("poppins") -> "Poppins"
                    nameLower.contains("roboto") -> "Roboto"
                    nameLower.contains("montserrat") -> "Montserrat"
                    nameLower.contains("lato") -> "Lato"
                    else -> null
                }

                val resolved: FontFamily = if (knownGoogleFont != null) {
                    try {
                        Log.d("BackendFontResolver", "Attempting to load Google Font: '$knownGoogleFont' for backend name '$nameTrim'")
                        val provider = GoogleFont.Provider(
                            providerAuthority = "com.google.android.gms.fonts",
                            providerPackage = "com.google.android.gms",
                            certificates = R.array.com_google_android_gms_fonts_certs
                        )

                        val googleFont = GoogleFont(knownGoogleFont)

                        FontFamily(
                            Font(
                                googleFont = googleFont,
                                fontProvider = provider,
                                weight = weight,
                                style = style
                            )
                        ).also { Log.d("BackendFontResolver", "Loaded Google Font '$knownGoogleFont' for '$nameTrim'") }
                    } catch (e: Exception) {
                        Log.w("BackendFontResolver", "Failed to load Google Font '$knownGoogleFont' for '$nameTrim', falling back to system family", e)

                        when {
                            nameLower.contains("arial") || nameLower.contains("sans") || nameLower.contains("helvetica") -> FontFamily.SansSerif
                            nameLower.contains("times") || nameLower.contains("serif") -> FontFamily.Serif
                            nameLower.contains("mono") || nameLower.contains("courier") || nameLower.contains("monospace") -> FontFamily.Monospace
                            else -> FontFamily.Default
                        }
                    }
                } else {
                    // No known Google equivalent — try direct GoogleFont loading for the provided name
                    try {
                        Log.d("BackendFontResolver", "Attempting to load Google Font: '$nameTrim'")
                        val provider = GoogleFont.Provider(
                            providerAuthority = "com.google.android.gms.fonts",
                            providerPackage = "com.google.android.gms",
                            certificates = R.array.com_google_android_gms_fonts_certs
                        )

                        val googleFont = GoogleFont(nameTrim)

                        FontFamily(
                            Font(
                                googleFont = googleFont,
                                fontProvider = provider,
                                weight = weight,
                                style = style
                            )
                        ).also { Log.d("BackendFontResolver", "Loaded Google Font '$nameTrim'") }
                    } catch (e: Exception) {
                        Log.w("BackendFontResolver", "Failed to load Google Font '$nameTrim', falling back to system family", e)

                        when {
                            nameLower.contains("arial") || nameLower.contains("sans") || nameLower.contains("helvetica") -> FontFamily.SansSerif
                            nameLower.contains("times") || nameLower.contains("serif") -> FontFamily.Serif
                            nameLower.contains("mono") || nameLower.contains("courier") || nameLower.contains("monospace") -> FontFamily.Monospace
                            else -> FontFamily.Default
                        }
                    }
                }

                // Cache resolved FontFamily
                synchronized(fontFamilyCache) { fontFamilyCache[cacheKey] = resolved }
                Log.d("BackendFontResolver", "Resolved fontFamilyName='$fontFamilyName' -> $resolved (cached)")
                resolved
            } catch (e: Exception) {
                Log.w("BackendFontResolver", "Failed to resolve font '$fontFamilyName', falling back to Default", e)
                FontFamily.Default
            }
        }
    }
}


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
