package com.door43.translationstudio.ui.translate.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

@Composable
fun ShrinkableTabRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val maxWidth = constraints.maxWidth

        val naturalWidths = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val totalNatural = naturalWidths.sum()

        val placeables = if (totalNatural <= maxWidth) {
            measurables.mapIndexed { i, m ->
                m.measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = naturalWidths[i],
                        minHeight = 0,
                        maxHeight = constraints.maxHeight
                    )
                )
            }
        } else {
            val share = maxWidth / measurables.size
            measurables.map {
                it.measure(
                    Constraints(
                        minWidth = 0,
                        maxWidth = share,
                        minHeight = 0,
                        maxHeight = constraints.maxHeight
                    )
                )
            }
        }

        val height = placeables.maxOf { it.height }
        val totalWidth = placeables.sumOf { it.width }

        layout(totalWidth.coerceAtMost(maxWidth), height) {
            var x = 0
            placeables.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width
            }
        }
    }
}