package com.example.carousal.deeplink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.carousal.App

/** Shared frame: asks AppStorys for this screen's campaigns, so the dashboard can target it. */
@Composable
private fun LinkedScreen(
    padding: PaddingValues,
    title: String,
    sdkScreenName: String,
    content: @Composable ColumnScope.() -> Unit
) {
    LaunchedEffect(sdkScreenName) {
        App.appStorys.getScreenCampaigns(sdkScreenName, emptyList())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
fun ProductScreen(padding: PaddingValues, productId: String) =
    LinkedScreen(padding, "Product #$productId", "Product Screen Kotlin") {
        Text("Opened from appstorys://demo/product/$productId")
    }

@Composable
fun OffersScreen(padding: PaddingValues, code: String?) =
    LinkedScreen(padding, "Offers", "Offers Screen Kotlin") {
        Text(if (code == null) "No code in the link" else "Code applied: $code")
    }

@Composable
fun ProfileScreen(padding: PaddingValues, onLogout: () -> Unit) =
    LinkedScreen(padding, "Profile", "Profile Screen Kotlin") {
        Text("Only reachable when logged in")
        Button(onClick = onLogout) { Text("Log out") }
    }

@Composable
fun LoginScreen(padding: PaddingValues, next: String?, onLogin: () -> Unit) =
    LinkedScreen(padding, "Log in", "Login Screen Kotlin") {
        Text(if (next == null) "Log in to continue" else "Log in to open: $next")
        Button(onClick = onLogin) { Text("Log in (demo)") }
    }
