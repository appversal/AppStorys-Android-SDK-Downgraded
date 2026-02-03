package com.appversal.appstorys.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.appversal.appstorys.api.MilestoneItem
import com.appversal.appstorys.api.MilestoneStyling

@Composable
internal fun MilestoneBanner(
    milestoneItem: MilestoneItem,
    styling: MilestoneStyling?,
    bottomPadding: Dp,
    onClose: () -> Unit,
    onClick: () -> Unit
) {

    val bannerStyling = styling?.banner

    val marginTop = bannerStyling?.marginTop?.toIntOrNull()?.dp ?: 0.dp
    val marginBottom = bannerStyling?.marginBottom?.toIntOrNull()?.dp ?: 0.dp
    val marginLeft = bannerStyling?.marginLeft?.toIntOrNull()?.dp ?: 0.dp
    val marginRight = bannerStyling?.marginRight?.toIntOrNull()?.dp ?: 0.dp

    val borderRadiusTopLeft = bannerStyling?.borderRadiusTopLeft?.toIntOrNull()?.dp ?: 0.dp
    val borderRadiusTopRight = bannerStyling?.borderRadiusTopRight?.toIntOrNull()?.dp ?: 0.dp
    val borderRadiusBottomLeft = bannerStyling?.borderRadiusBottomLeft?.toIntOrNull()?.dp ?: 0.dp
    val borderRadiusBottomRight = bannerStyling?.borderRadiusBottomRight?.toIntOrNull()?.dp ?: 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    top = marginTop,
                    bottom = marginBottom,
                    start = marginLeft,
                    end = marginRight
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(milestoneItem.image)
                    .crossfade(true)
                    .build(),
                contentDescription = "Milestone",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = borderRadiusTopLeft,
                            topEnd = borderRadiusTopRight,
                            bottomStart = borderRadiusBottomLeft,
                            bottomEnd = borderRadiusBottomRight
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
            )

            // Close button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0x4D000000))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun MilestoneModal(
    milestoneItem: MilestoneItem,
    styling: MilestoneStyling?,
    bottomPadding: Dp,
    onClose: () -> Unit,
    onClick: () -> Unit
) {
//        val configuration = LocalConfiguration.current
//        val screenWidth = configuration.screenWidthDp.dp

    val bannerStyling = styling?.banner

    val marginTop = bannerStyling?.marginTop?.toIntOrNull()?.dp ?: 0.dp
    val marginBottom = bannerStyling?.marginBottom?.toIntOrNull()?.dp ?: 0.dp
    val marginLeft = bannerStyling?.marginLeft?.toIntOrNull()?.dp ?: 0.dp
    val marginRight = bannerStyling?.marginRight?.toIntOrNull()?.dp ?: 0.dp

    val borderRadiusTopLeft = bannerStyling?.borderRadiusTopLeft?.toIntOrNull()?.dp ?: 0.dp
    val borderRadiusTopRight = bannerStyling?.borderRadiusTopRight?.toIntOrNull()?.dp ?: 0.dp
    val borderRadiusBottomLeft = bannerStyling?.borderRadiusBottomLeft?.toIntOrNull()?.dp ?: 0.dp
    val borderRadiusBottomRight = bannerStyling?.borderRadiusBottomRight?.toIntOrNull()?.dp ?: 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = marginTop,
                    bottom = marginBottom,
                    start = marginLeft,
                    end = marginRight
                )
                .clip(
                    RoundedCornerShape(
                        topStart = borderRadiusTopLeft,
                        topEnd = borderRadiusTopRight,
                        bottomStart = borderRadiusBottomLeft,
                        bottomEnd = borderRadiusBottomRight
                    )
                )
                .background(Color.White)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(milestoneItem.image)
                    .crossfade(true)
                    .build(),
                contentDescription = "Milestone",
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
            )

            // Close button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x4D000000))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
internal fun MilestoneWidgets(
    milestoneItem: MilestoneItem,
    styling: MilestoneStyling?,
    onClick: () -> Unit
) {
//        val configuration = LocalConfiguration.current
//        val screenWidth = configuration.screenWidthDp.dp

    val bannerStyling = styling?.banner

    val marginTop = bannerStyling?.marginTop?.toIntOrNull()?.dp ?: 0.dp
    val marginBottom = bannerStyling?.marginBottom?.toIntOrNull()?.dp ?: 0.dp
    val marginLeft = bannerStyling?.marginLeft?.toIntOrNull()?.dp ?: 0.dp
    val marginRight = bannerStyling?.marginRight?.toIntOrNull()?.dp ?: 0.dp

    val borderRadiusTopLeft = bannerStyling?.borderRadiusTopLeft?.toIntOrNull()?.dp ?: 0.dp
    val borderRadiusTopRight = bannerStyling?.borderRadiusTopRight?.toIntOrNull()?.dp ?: 0.dp
    val borderRadiusBottomLeft = bannerStyling?.borderRadiusBottomLeft?.toIntOrNull()?.dp ?: 0.dp
    val borderRadiusBottomRight = bannerStyling?.borderRadiusBottomRight?.toIntOrNull()?.dp ?: 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .padding(
                    top = marginTop,
                    bottom = marginBottom,
                    start = marginLeft,
                    end = marginRight
                )
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(milestoneItem.image)
                    .crossfade(true)
                    .build(),
                contentDescription = "Milestone",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = borderRadiusTopLeft,
                            topEnd = borderRadiusTopRight,
                            bottomStart = borderRadiusBottomLeft,
                            bottomEnd = borderRadiusBottomRight
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
            )
        }
    }
}