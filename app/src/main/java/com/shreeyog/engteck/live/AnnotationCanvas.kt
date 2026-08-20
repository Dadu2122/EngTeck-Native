package com.shreeyog.engteck.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

enum class AnnotationTool { POINTER, MARKER, HIGHLIGHTER, ERASER, RECTANGLE, CIRCLE, LINE, ARROW }

data class InkShape(
    val tool: AnnotationTool,
    val color: Color,
    val width: Float,
    val start: Offset,
    val end: Offset,
    val path: Path? = null
)

@Composable
fun AnnotationCanvas(
    modifier: Modifier = Modifier,
    tool: AnnotationTool,
    color: Color,
    penWidth: Float,
    strokes: SnapshotStateList<InkShape>,
    redoStack: SnapshotStateList<InkShape>
) {
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var freehandPath by remember { mutableStateOf<Path?>(null) }

    Canvas(
        modifier = modifier.pointerInput(tool, color, penWidth) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragStart = offset
                    dragCurrent = offset
                    if (tool == AnnotationTool.MARKER || tool == AnnotationTool.HIGHLIGHTER) {
                        freehandPath = Path().apply { moveTo(offset.x, offset.y) }
                    }
                },
                onDrag = { change, _ ->
                    dragCurrent = change.position
                    if (tool == AnnotationTool.MARKER || tool == AnnotationTool.HIGHLIGHTER) {
                        freehandPath?.lineTo(change.position.x, change.position.y)
                        freehandPath = freehandPath
                    }
                },
                onDragEnd = {
                    val end = dragCurrent ?: dragStart
                    when (tool) {
                        AnnotationTool.ERASER -> strokes.clear()
                        AnnotationTool.MARKER -> {
                            freehandPath?.let { strokes.add(InkShape(tool, color, penWidth, dragStart, end, it)) }
                        }
                        AnnotationTool.HIGHLIGHTER -> {
                            freehandPath?.let { strokes.add(InkShape(tool, color.copy(alpha = 0.4f), penWidth * 3, dragStart, end, it)) }
                        }
                        AnnotationTool.RECTANGLE, AnnotationTool.CIRCLE, AnnotationTool.LINE, AnnotationTool.ARROW -> {
                            if (abs(end.x - dragStart.x) > 4 || abs(end.y - dragStart.y) > 4) {
                                strokes.add(InkShape(tool, color, penWidth, dragStart, end))
                            }
                        }
                        AnnotationTool.POINTER -> {}
                    }
                    if (strokes.isNotEmpty()) redoStack.clear()
                    freehandPath = null
                    dragCurrent = null
                }
            )
        }
    ) {
        strokes.forEach { shape -> drawShape(shape) }

        val liveEnd = dragCurrent
        if (liveEnd != null) {
            when (tool) {
                AnnotationTool.MARKER -> freehandPath?.let { drawPath(it, color = color, style = Stroke(width = penWidth)) }
                AnnotationTool.HIGHLIGHTER -> freehandPath?.let { drawPath(it, color = color.copy(alpha = 0.4f), style = Stroke(width = penWidth * 3)) }
                AnnotationTool.RECTANGLE -> drawRect(
                    color = color,
                    topLeft = Offset(minOf(dragStart.x, liveEnd.x), minOf(dragStart.y, liveEnd.y)),
                    size = androidx.compose.ui.geometry.Size(abs(liveEnd.x - dragStart.x), abs(liveEnd.y - dragStart.y)),
                    style = Stroke(width = penWidth)
                )
                AnnotationTool.CIRCLE -> drawOval(
                    color = color,
                    topLeft = Offset(minOf(dragStart.x, liveEnd.x), minOf(dragStart.y, liveEnd.y)),
                    size = androidx.compose.ui.geometry.Size(abs(liveEnd.x - dragStart.x), abs(liveEnd.y - dragStart.y)),
                    style = Stroke(width = penWidth)
                )
                AnnotationTool.LINE, AnnotationTool.ARROW -> drawLine(color, dragStart, liveEnd, strokeWidth = penWidth)
                else -> {}
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShape(shape: InkShape) {
    when (shape.tool) {
        AnnotationTool.MARKER, AnnotationTool.HIGHLIGHTER -> {
            shape.path?.let { drawPath(it, color = shape.color, style = Stroke(width = shape.width)) }
        }
        AnnotationTool.RECTANGLE -> drawRect(
            color = shape.color,
            topLeft = Offset(minOf(shape.start.x, shape.end.x), minOf(shape.start.y, shape.end.y)),
            size = androidx.compose.ui.geometry.Size(abs(shape.end.x - shape.start.x), abs(shape.end.y - shape.start.y)),
            style = Stroke(width = shape.width)
        )
        AnnotationTool.CIRCLE -> drawOval(
            color = shape.color,
            topLeft = Offset(minOf(shape.start.x, shape.end.x), minOf(shape.start.y, shape.end.y)),
            size = androidx.compose.ui.geometry.Size(abs(shape.end.x - shape.start.x), abs(shape.end.y - shape.start.y)),
            style = Stroke(width = shape.width)
        )
        AnnotationTool.LINE -> drawLine(shape.color, shape.start, shape.end, strokeWidth = shape.width)
        AnnotationTool.ARROW -> {
            drawLine(shape.color, shape.start, shape.end, strokeWidth = shape.width)
            val angle = kotlin.math.atan2((shape.end.y - shape.start.y).toDouble(), (shape.end.x - shape.start.x).toDouble())
            val arrowLen = 24f
            val a1 = Offset(
                (shape.end.x - arrowLen * kotlin.math.cos(angle - 0.4)).toFloat(),
                (shape.end.y - arrowLen * kotlin.math.sin(angle - 0.4)).toFloat()
            )
            val a2 = Offset(
                (shape.end.x - arrowLen * kotlin.math.cos(angle + 0.4)).toFloat(),
                (shape.end.y - arrowLen * kotlin.math.sin(angle + 0.4)).toFloat()
            )
            drawLine(shape.color, shape.end, a1, strokeWidth = shape.width)
            drawLine(shape.color, shape.end, a2, strokeWidth = shape.width)
        }
        else -> {}
    }
}
