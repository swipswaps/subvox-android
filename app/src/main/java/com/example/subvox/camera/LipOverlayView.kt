package com.example.subvox.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class LipOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var landmarks: List<NormalizedLandmark>? = null
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    // MediaPipe Face Mesh indices for the outer and inner lips
    private val lipIndices = intArrayOf(
        61, 146, 91, 181, 84, 17, 314, 405, 321, 375, 291, 409, 270, 269, 267, 0, 37, 39, 40, 185
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = landmarks ?: return
        if (pts.size < 468) return

        // Draw outer lip boundary
        for (i in lipIndices.indices) {
            val p1 = pts[lipIndices[i]]
            val p2 = pts[lipIndices[(i + 1) % lipIndices.size]]
            canvas.drawLine(
                p1.x() * width, p1.y() * height,
                p2.x() * width, p2.y() * height, paint
            )
        }
    }
}
