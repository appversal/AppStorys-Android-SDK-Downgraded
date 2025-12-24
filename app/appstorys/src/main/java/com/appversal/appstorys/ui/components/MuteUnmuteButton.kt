package com.appversal.appstorys.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.appversal.appstorys.R
import com.appversal.appstorys.api.SoundToggle

@Composable
internal fun MuteUnmuteButton(
    size: Dp = 18.dp,
    modifier: Modifier,
    isMuted: Boolean,
    soundToggle: SoundToggle?,
    muteButtonImageUrl: String?,
    unmuteButtonImageUrl: String?,
    applyMargins: Boolean = true,  // Set to false to ignore backend margins (for maximized view)
    boundaryPadding: Dp? = null,
    onToggleMute: () -> Unit
) {
    // Get the appropriate config based on mute state
    val muteConfig = soundToggle?.mute
    val unmuteConfig = soundToggle?.unmute
    val safePadding = boundaryPadding ?: 0.dp

    val fillColor = if (isMuted) {
        try {
            muteConfig?.colors?.fill?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } else {
        try {
            unmuteConfig?.colors?.fill?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } ?: Color.Black.copy(alpha = 0.7f)

    val iconColor = if (isMuted) {
        try {
            muteConfig?.colors?.cross?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } else {
        try {
            unmuteConfig?.colors?.cross?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } ?: Color.White

    val strokeColor = if (isMuted) {
        try {
            muteConfig?.colors?.stroke?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    } else {
        try {
            unmuteConfig?.colors?.stroke?.let { Color(it.toColorInt()) }
        } catch (_: Exception) {
            null
        }
    }

    // Extract margins - only apply if applyMargins is true
    val topMargin = if (applyMargins) {
        if (isMuted) {
            try {
                muteConfig?.margin?.top?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } else {
            try {
                unmuteConfig?.margin?.top?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } ?: 0.dp
    } else 0.dp

    val leftMargin = if (applyMargins) {
        if (isMuted) {
            try {
                muteConfig?.margin?.left?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } else {
            try {
                unmuteConfig?.margin?.left?.toIntOrNull()?.dp
            } catch (_: Exception) {
                null
            }
        } ?: 0.dp
    } else 0.dp

    Box(
        modifier = modifier
            .padding(
                top = topMargin + safePadding,
                start = leftMargin  + safePadding,

            )
            .size(size)
            .clip(CircleShape)
            .background(fillColor)
            .then(
                if (strokeColor != null) {
                    Modifier.border(1.dp, strokeColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable { onToggleMute() },
        contentAlignment = Alignment.Center
    ) {
        when {
            isMuted && !muteButtonImageUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = muteButtonImageUrl,
                    contentDescription = "Mute",
                    modifier = Modifier.padding(4.dp)
                )
            }
            !isMuted && !unmuteButtonImageUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = unmuteButtonImageUrl,
                    contentDescription = "Unmute",
                    modifier = Modifier.padding(4.dp)
                )
            }
            else -> {
                Icon(
                    painter = if (isMuted) painterResource(R.drawable.mute) else painterResource(R.drawable.volume),
                    contentDescription = if (isMuted) "Mute" else "Unmute",
                    tint = iconColor,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

    }
}
