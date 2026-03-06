package com.door43.translationstudio.ui.translate.components.review

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReviewCard(
    isResourcesExpanded: Boolean,
    onToggleResources: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mainCardsWeight by animateFloatAsState(
        targetValue = if (isResourcesExpanded) 1f / 3f else 0.475f,
        label = "mainCardsWeight"
    )
    val resourcesWeight by animateFloatAsState(
        targetValue = if (isResourcesExpanded) 1f / 3f else 0.05f,
        label = "resourcesWeight"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Height needs to be fixed or intrinsically calculated so cards match height
            .height(250.dp)
            .padding(vertical = 8.dp)
    ) {
        SourceCard(
            modifier = Modifier
                .weight(mainCardsWeight)
                .fillMaxHeight()
                .padding(horizontal = 4.dp)
        )

        TargetCard(
            modifier = Modifier
                .weight(mainCardsWeight)
                .fillMaxHeight()
                .padding(horizontal = 4.dp)
        )

        ResourcesCard(
            isExpanded = isResourcesExpanded,
            onToggle = onToggleResources,
            modifier = Modifier
                .weight(resourcesWeight)
                .fillMaxHeight()
                .padding(horizontal = 4.dp)
        )
    }
}