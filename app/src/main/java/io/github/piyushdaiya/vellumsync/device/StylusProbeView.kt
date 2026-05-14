package io.github.piyushdaiya.vellumsync.device

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

class StylusProbeView(
    context: Context,
    private val onStylusDetected: () -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 42f
        strokeWidth = 4f
    }

    private var lastToolType: Int = MotionEvent.TOOL_TYPE_UNKNOWN
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex.coerceAtLeast(0)
        lastToolType = event.getToolType(index)
        lastX = event.x
        lastY = event.y

        if (lastToolType == MotionEvent.TOOL_TYPE_STYLUS ||
            lastToolType == MotionEvent.TOOL_TYPE_ERASER
        ) {
            onStylusDetected()
        }

        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val label = when (lastToolType) {
            MotionEvent.TOOL_TYPE_STYLUS -> "Stylus detected"
            MotionEvent.TOOL_TYPE_ERASER -> "Stylus eraser detected"
            MotionEvent.TOOL_TYPE_FINGER -> "Finger detected"
            MotionEvent.TOOL_TYPE_MOUSE -> "Mouse detected"
            else -> "Touch this area with your pen"
        }

        canvas.drawText(label, 32f, 72f, paint)
        canvas.drawText("x=$lastX y=$lastY", 32f, 132f, paint)
    }
}
