package io.github.piyushdaiya.vellumsync.device

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class StylusProbeView(
    context: Context,
    private val onProbe: (StylusProbeResult) -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 42f
        strokeWidth = 4f
    }

    private var lastResult = StylusProbeResult(
        toolTypeName = "UNKNOWN",
        stylusDetected = false,
        x = 0f,
        y = 0f,
        pressure = 0f,
        size = 0f
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex.coerceAtLeast(0).coerceAtMost(event.pointerCount - 1)
        val toolType = event.getToolType(index)
        val stylusDetected = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
            toolType == MotionEvent.TOOL_TYPE_ERASER

        lastResult = StylusProbeResult(
            toolTypeName = toolTypeName(toolType),
            stylusDetected = stylusDetected,
            x = event.getX(index),
            y = event.getY(index),
            pressure = event.getPressure(index),
            size = event.getSize(index)
        )

        onProbe(lastResult)
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val label = when (lastResult.toolTypeName) {
            "STYLUS" -> "Stylus detected"
            "ERASER" -> "Stylus eraser detected"
            "FINGER" -> "Finger detected"
            "MOUSE" -> "Mouse detected"
            else -> "Touch this area with your pen"
        }

        canvas.drawText(label, 32f, 72f, paint)
        canvas.drawText("x=${lastResult.x} y=${lastResult.y}", 32f, 132f, paint)
        canvas.drawText(
            "pressure=${lastResult.pressure} size=${lastResult.size}",
            32f,
            192f,
            paint
        )
    }

    private fun toolTypeName(toolType: Int): String {
        return when (toolType) {
            MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
            MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
            MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
            MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
            MotionEvent.TOOL_TYPE_UNKNOWN -> "UNKNOWN"
            else -> "TOOL_$toolType"
        }
    }
}

data class StylusProbeResult(
    val toolTypeName: String,
    val stylusDetected: Boolean,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float
)
