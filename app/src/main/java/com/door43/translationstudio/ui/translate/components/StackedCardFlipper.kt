package com.door43.translationstudio.ui.translate.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Composable
fun StackedCardFlipper(
    modifier: Modifier = Modifier,
    containerPadding: Dp = 8.dp,
    stackOffset: Dp = 16.dp,
    onAnimationEnd: (isFrontOnTop: Boolean) -> Unit = {},
    frontCard: @Composable () -> Unit,
    backCard: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    val topOffset = 0.dp

    val frontX = remember { Animatable(topOffset, Dp.VectorConverter) }
    val frontY = remember { Animatable(topOffset, Dp.VectorConverter) }
    val frontZ = remember { Animatable(1f) }

    val backX = remember { Animatable(stackOffset, Dp.VectorConverter) }
    val backY = remember { Animatable(stackOffset, Dp.VectorConverter) }
    val backZ = remember { Animatable(0f) }

    var isFrontOnTop by remember { mutableStateOf(true) }
    var isAnimating by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(containerPadding),
        contentAlignment = Alignment.Center
    ) {
        val halfWidth = maxWidth / 2

        fun triggerFlipAnimation(leftToRight: Boolean) {
            if (isAnimating) return
            isAnimating = true
            
            coroutineScope.launch {
                val duration = 400 
                val easing = LinearEasing 

                val topOutDelta = if (leftToRight) halfWidth else -halfWidth
                val bottomOutDelta = if (leftToRight) -halfWidth else halfWidth

                val job1 = launch { frontX.animateTo(frontX.value + topOutDelta, tween(duration, easing = easing)) }
                val job2 = launch { backX.animateTo(backX.value + bottomOutDelta, tween(duration, easing = easing)) }
                joinAll(job1, job2) 

                isFrontOnTop = !isFrontOnTop
                frontZ.snapTo(if (isFrontOnTop) 1f else 0f)
                backZ.snapTo(if (isFrontOnTop) 0f else 1f)

                val destFrontX = if (isFrontOnTop) topOffset else stackOffset
                val destFrontY = if (isFrontOnTop) topOffset else stackOffset
                val destBackX = if (isFrontOnTop) stackOffset else topOffset
                val destBackY = if (isFrontOnTop) stackOffset else topOffset

                val job3 = launch {
                    launch { frontX.animateTo(destFrontX, tween(duration, easing = easing)) }
                    launch { frontY.animateTo(destFrontY, tween(duration, easing = easing)) }
                }
                val job4 = launch {
                    launch { backX.animateTo(destBackX, tween(duration, easing = easing)) }
                    launch { backY.animateTo(destBackY, tween(duration, easing = easing)) }
                }
                joinAll(job3, job4)

                isAnimating = false
                onAnimationEnd(isFrontOnTop)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .height(IntrinsicSize.Max)
                .pointerInput(Unit) {
                    var accumulatedDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onDragEnd = {
                            if (accumulatedDrag > 50) triggerFlipAnimation(leftToRight = true)
                            else if (accumulatedDrag < -50) triggerFlipAnimation(leftToRight = false)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                    }
                }
        ) {
            val interactionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = stackOffset, bottom = stackOffset)
                    .offset(x = backX.value, y = backY.value)
                    .zIndex(backZ.value)
                    .clickable(
                        enabled = isFrontOnTop,
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        triggerFlipAnimation(leftToRight = true)
                    }
            ) {
                backCard()
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = stackOffset, bottom = stackOffset)
                    .offset(x = frontX.value, y = frontY.value)
                    .zIndex(frontZ.value)
                    .clickable(
                        enabled = !isFrontOnTop,
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        triggerFlipAnimation(leftToRight = true)
                    }
            ) {
                frontCard()
            }
        }
    }
}