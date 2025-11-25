package com.appversal.appstorys.utils

import androidx.compose.ui.text.googlefonts.GoogleFont
import com.appversal.appstorys.R

internal val googleFontProvider: GoogleFont.Provider
    get() = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )