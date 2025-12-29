package com.appversal.appstorys.ui.modals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage

@Composable
internal fun ModalImageOnly(
    imageUrl: String?,
    appearanceHeightDp: Dp,
    containerShape: RoundedCornerShape,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Popup Image",
            modifier = Modifier
                .fillMaxWidth()
                .height(appearanceHeightDp)
                .clip(containerShape),
            contentScale = ContentScale.Crop
        )
    }
}

