package com.numandegirmenci.belgeizi.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun SignatureDialog(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var active by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("İmzanı çiz") },
        text = {
            Column {
                Text("Parmağınla imza at. Çizim şeffaf PNG olarak belgeye eklenir.",
                    color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Canvas(
                    Modifier.fillMaxWidth().height(205.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .onSizeChanged { boardSize = it }
                        .pointerInput(boardSize) {
                            detectDragGestures(
                                onDragStart = { p ->
                                    if (boardSize.width > 0 && boardSize.height > 0) {
                                        active = listOf(Offset(
                                            (p.x / boardSize.width).coerceIn(0f, 1f),
                                            (p.y / boardSize.height).coerceIn(0f, 1f)
                                        ))
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (boardSize.width > 0 && boardSize.height > 0) {
                                        active = active + Offset(
                                            (change.position.x / boardSize.width).coerceIn(0f, 1f),
                                            (change.position.y / boardSize.height).coerceIn(0f, 1f)
                                        )
                                    }
                                },
                                onDragEnd = {
                                    if (active.isNotEmpty()) strokes.add(active)
                                    active = emptyList()
                                },
                                onDragCancel = { active = emptyList() }
                            )
                        }
                ) {
                    (strokes.toList() + listOf(active)).forEach { points ->
                        if (points.size >= 2) {
                            val path = Path().apply {
                                moveTo(points.first().x * size.width, points.first().y * size.height)
                                points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                            }
                            drawPath(path, color = Color(0xFF163658),
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                    drawLine(color = Color(0xFFE0E7EF),
                        start = Offset(20f, size.height * 0.82f),
                        end = Offset(size.width - 20f, size.height * 0.82f), strokeWidth = 1f)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (active.isNotEmpty()) strokes.add(active)
                if (strokes.any { it.size >= 2 }) onSave(renderSignature(strokes.toList()))
            }, enabled = strokes.isNotEmpty() || active.size >= 2) { Text("İmzayı ekle") }
        },
        dismissButton = {
            TextButton(onClick = { strokes.clear(); active = emptyList() }) { Text("Temizle") }
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

/** Preserves transparency for compositing on any PDF background. */
private fun renderSignature(strokes: List<List<Offset>>): Bitmap {
    val bitmap = Bitmap.createBitmap(960, 400, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val pen = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(18, 45, 74)
        style = AndroidPaint.Style.STROKE
        strokeWidth = 6f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    strokes.forEach { points ->
        if (points.size >= 2) {
            val path = AndroidPath().apply {
                moveTo(points.first().x * 960, points.first().y * 400)
                points.drop(1).forEach { lineTo(it.x * 960, it.y * 400) }
            }
            canvas.drawPath(path, pen)
        }
    }
    return bitmap
}
