package com.appversal.appstorys.ui.scratchcard

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun TermsAndConditionsView(
    onDismiss: () -> Unit,
    termsHtml: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // HTML content
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    android.widget.TextView(context).apply {
                        // Set text appearance
                        setTextAppearance(android.R.style.TextAppearance_Material_Body1)

                        // Parse HTML
                        text =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                android.text.Html.fromHtml(
                                    termsHtml,
                                    android.text.Html.FROM_HTML_MODE_COMPACT
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                android.text.Html.fromHtml(termsHtml)
                            }

                        // Make links clickable
                        movementMethod = android.text.method.LinkMovementMethod.getInstance()

                        // Set text size
                        textSize = 14f

                        // Set padding
                        setPadding(0, 0, 0, 0)
                    }
                },
                update = { textView ->
                    textView.text =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            android.text.Html.fromHtml(
                                termsHtml,
                                android.text.Html.FROM_HTML_MODE_COMPACT
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            android.text.Html.fromHtml(termsHtml)
                        }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Helper function to map point to grid cell index
