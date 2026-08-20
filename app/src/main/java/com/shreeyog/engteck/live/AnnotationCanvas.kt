package com.shreeyog.engteck.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

enum class AnnotationTool { POINTER, MOVE, MARKER, HIGHLIGHTER, ERASER, RECTANGLE, CIRCLE, LINE, ARROW }

data class InkShape(
    val tool: AnnotationTool,
    val color: Color,
    val width: Float,
    val start: Offset,
    val end: Offset,
    val points: List<Offset> = emptyList()
)

private fun InkShape.bounds(): Pair<Offset, Offset> {
    return if (points.isNotEmpty()) {
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        Offset(minX, minY) to Offset(maxX, maxY)
    } else {
        Offset(minOf(start.x, end.x), minOf(start.y, end.y)) to Offset(maxOf(start.x, end.x), maxOf(start.y, end.y))
    }
}

private fun InkShape.containsPoint(p: Offset, pad: Float = 24f): Boolean {
    val (min, max) = bounds()
    return p.x in (min.x - pad)..(max.x + pad) && p.y in (min.y - pad)..(max.y + pad)
}

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
    var freehandPoints by remember { mutableStateOf<MutableList<Offset>>(mutableListOf()) }
    var moveIndex by remember { mutableStateOf(-1) }
    var moveLast by remember { mutableStateOf(Offset.Zero) }
    var pointerPos by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier.pointerInput(tool, color, penWidth) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragStart = offset
                    dragCurrent = offset
                    when (tool) {
                        AnnotationTool.MARKER, AnnotationTool.HIGHLIGHTER -> {
                            freehandPoints = mutableListOf(offset)
                        }
                        AnnotationTool.MOVE -> {
                            moveIndex = strokes.indexOfLast { it.containsPoint(offset) }
                            moveLast = offset
                        }
                        AnnotationTool.POINTER -> { pointerPos = offset }
                        else -> {}
                    }
                },
                onDrag = { change, _ ->
                    dragCurrent = change.position
                    when (tool) {
                        AnnotationTool.MARKER, AnnotationTool.HIGHLIGHTER -> {
                            freehandPoints = (freehandPoints + change.position).toMutableList()
                        }
                        AnnotationTool.MOVE -> {
                            if (moveIndex >= 0 && moveIndex < strokes.size) {
                                val delta = change.position - moveLast
                                val old = strokes[moveIndex]
                                strokes[moveIndex] = old.copy(
                                    start = old.start + delta,
                                    end = old.end + delta,
                                    points = old.points.map { it + delta }
                                )
                                moveLast = change.position
                            }
                        }
                        AnnotationTool.POINTER -> { pointerPos = change.position }
                        else -> {}
                    }
                },
                onDragEnd = {
                    val end = dragCurrent ?: dragStart
                    when (tool) {
                        AnnotationTool.ERASER -> strokes.clear()
                        AnnotationTool.MARKER -> {
                            if (freehandPoints.size > 1) strokes.add(InkShape(tool, color, penWidth, dragStart, end, freehandPoints.toList()))
                        }
                        AnnotationTool.HIGHLIGHTER -> {
                            if (freehandPoints.size > 1) strokes.add(InkShape(tool, color.copy(alpha = 0.4f), penWidth * 3, dragStart, end, freehandPoints.toList()))
                        }
                        AnnotationTool.RECTANGLE, AnnotationTool.CIRCLE, AnnotationTool.LINE, AnnotationTool.ARROW -> {
                            if (abs(end.x - dragStart.x) > 4 || abs(end.y - dragStart.y) > 4) {
                                strokes.add(InkShape(tool, color, penWidth, dragStart, end))
                            }
                        }
                        AnnotationTool.MOVE -> { moveIndex = -1 }
                        AnnotationTool.POINTER -> { pointerPos = null }
                    }
                    if (tool != AnnotationTool.MOVE && tool != AnnotationTool.POINTER && strokes.isNotEmpty()) redoStack.clear()
                    freehandPoints = mutableListOf()
                    dragCurrent = null
                }
            )
        }
    ) {
        strokes.forEach { shape: InkShape -> drawShape(shape) }

        val liveEnd = dragCurrent
        if (liveEnd != null) {
            when (tool) {
                AnnotationTool.MARKER -> if (freehandPoints.size > 1) drawPath(buildFreehandPath(freehandPoints), color = color, style = Stroke(width = penWidth))
                AnnotationTool.HIGHLIGHTER -> if (freehandPoints.size > 1) drawPath(buildFreehandPath(freehandPoints), color = color.copy(alpha = 0.4f), style = Stroke(width = penWidth * 3))
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

        // Pointer: hollow red circle following the touch (laser-pointer style)
        val pp = pointerPos
        if (tool == AnnotationTool.POINTER && pp != null) {
            drawCircle(color = Color(0xFFE53935), radius = 16f, center = pp, style = Stroke(width = 4f))
        }
    }
}

private fun buildFreehandPath(points: List<Offset>): Path {
    val p = Path()
    if (points.isEmpty()) return p
    p.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) p.lineTo(points[i].x, points[i].y)
    return p
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShape(shape: InkShape) {
    when (shape.tool) {
        AnnotationTool.MARKER, AnnotationTool.HIGHLIGHTER -> {
            if (shape.points.size > 1) drawPath(buildFreehandPath(shape.points), color = shape.color, style = Stroke(width = shape.width))
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
