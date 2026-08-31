package com.workoutpose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Compose skeleton overlay for a [PoseFrame].
 *
 * Landmark x/y are normalized (0..1) in MediaPipe image space (x right, y down).
 * Set [mirror] to the same value applied to your camera preview (selfie mirroring).
 */
@Composable
fun PoseSkeletonOverlay(
    frame: PoseFrame?,
    modifier: Modifier = Modifier,
    skeletonColor: Color = Color(0xFF00FF00),
    landmarkColor: Color = Color(0xFFFF5252),
    minVisibility: Float = 0.5f,
    mirror: Boolean = true,
    strokeWidth: Float = 6f,
) {
    val buffer = frame?.landmarks ?: FloatArray(0)

    Canvas(modifier = modifier) {
        if (buffer.size < PoseGeometry.LANDMARK_COUNT * 4) return@Canvas

        val width = size.width
        val height = size.height

        fun xOf(index: Int): Float {
            val nx = buffer[index * 4]
            return (if (mirror) 1f - nx else nx) * width
        }

        val connectionPath = Path()
        for (connection in PoseGeometry.POSE_CONNECTIONS) {
            val i = connection[0]
            val j = connection[1]
            if (PoseGeometry.visibilityOf(buffer, i) < minVisibility) continue
            if (PoseGeometry.visibilityOf(buffer, j) < minVisibility) continue
            connectionPath.moveTo(xOf(i), buffer[i * 4 + 1] * height)
            connectionPath.lineTo(xOf(j), buffer[j * 4 + 1] * height)
        }
        drawPath(
            path = connectionPath,
            color = skeletonColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        for (i in 0 until PoseGeometry.LANDMARK_COUNT) {
            val visibility = PoseGeometry.visibilityOf(buffer, i)
            if (visibility < minVisibility) continue
            val center = Offset(xOf(i), buffer[i * 4 + 1] * height)
            val radius = if (visibility > 0.8f) 7f else 5f
            drawCircle(color = landmarkColor, radius = radius, center = center)
            drawCircle(
                color = skeletonColor,
                radius = radius + 1.5f,
                center = center,
                style = Stroke(width = 2f),
            )
        }
    }
}