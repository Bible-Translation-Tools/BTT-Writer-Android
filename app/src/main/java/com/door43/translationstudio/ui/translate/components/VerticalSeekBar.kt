package com.door43.translationstudio.ui.translate.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalSeekBar(
    sliderValue: Float = 0f,
    onSliderValueChange: (Float) -> Unit,
    tooltipLabel: String? = null
) {
    val safeSliderValue = if (sliderValue.isNaN()) 0f else sliderValue

    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    var internalDragValue by remember { mutableFloatStateOf(sliderValue) }

    LaunchedEffect(sliderValue) {
        if (!isDragged) {
            internalDragValue = sliderValue
        }
    }

    val displayValue = if (isDragged) internalDragValue else safeSliderValue

    var containerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val bubbleSizeDp = 72.dp

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .onSizeChanged { containerHeightPx = it.height }
    ) {
        Slider(
            value = if (displayValue.isNaN()) 0f else displayValue,
            onValueChange = {
                internalDragValue = it
                onSliderValueChange(it)
            },
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.5f)
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(color = Color.White, shape = CircleShape)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(2.dp),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        inactiveTrackColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
            },
            modifier = Modifier
                .padding(vertical = 16.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(
                            x = -(placeable.width / 2 - placeable.height / 2),
                            y = -(placeable.height / 2 - placeable.width / 2)
                        )
                    }
                }
                .graphicsLayer {
                    rotationZ = 90f
                }
        )

        // Chapter number tooltip shown during drag
        if (isDragged && tooltipLabel != null && containerHeightPx > 0) {
            val bubbleSizePx = with(density) { bubbleSizeDp.roundToPx() }
            val paddingPx = with(density) { 16.dp.roundToPx() }
            val usableHeight = containerHeightPx - 2 * paddingPx
            val thumbY = paddingPx + (displayValue * usableHeight).toInt()

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(
                    x = bubbleSizePx - 60,
                    y = thumbY - bubbleSizePx + 10
                ),
                properties = PopupProperties(clippingEnabled = false)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = bubbleSizeDp, height = bubbleSizeDp - 16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(
                                topStart = 32.dp,
                                topEnd = 32.dp,
                                bottomEnd = 32.dp,
                                bottomStart = 0.dp
                            )
                        )
                ) {
                    Text(
                        text = tooltipLabel,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
