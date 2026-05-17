package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStroke
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStrokeStyle
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport

/**
 * XML-backed note surface host.
 *
 * Compatibility note:
 * recent VellumSync patch lines introduced extra bindPage named parameters such as
 * `renderMode` and `renderedVisualPage`, while the currently active preview widget
 * still only consumes pageReport/transform/overlay fields. Accept those newer
 * parameters here as ignored compatibility shims so the app can compile across
 * mixed patch lines.
 */
class VellumNoteSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var preview: SupernoteVectorPreviewView? = null

    fun bindPage(
        pageReport: SupernoteStrokeGeometryPageReport?,
        transformMode: SupernotePreviewTransformMode,
        overlayEditingEnabled: Boolean,
        overlayEraserEnabled: Boolean,
        panZoomEnabled: Boolean,
        overlayVisible: Boolean,
        overlayStrokes: List<LocalAnnotationStroke>,
        currentOverlayStyle: LocalAnnotationStrokeStyle,
        onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit,
        renderMode: Any? = null,
        renderedVisualPage: Any? = null,
        visualPage: Any? = null
    ) {
        // marker=vellumsync-totalpath-tool-semantics-classifier-lasso-eraser-suppression-v0-compile-repair-2
        @Suppress("UNUSED_VARIABLE")
        val ignoredCompatArgs = Triple(renderMode, renderedVisualPage, visualPage)

        if (pageReport == null) {
            preview = null
            removeAllViews()
            addView(noPageMessage(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            return
        }

        val existingPreview = preview
        val targetPreview = existingPreview ?: SupernoteVectorPreviewView(
            context = context,
            pageReport = pageReport,
            transformMode = transformMode,
            overlayEditingEnabled = overlayEditingEnabled,
            overlayEraserEnabled = overlayEraserEnabled,
            panZoomEnabled = panZoomEnabled,
            overlayVisible = overlayVisible,
            overlayStrokes = overlayStrokes,
            currentOverlayStyle = currentOverlayStyle,
            onOverlayChanged = onOverlayChanged
        ).also { created ->
            preview = created
            removeAllViews()
            addView(created, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        targetPreview.updatePageReport(
            pageReport = pageReport,
            transformMode = transformMode,
            overlayEditingEnabled = overlayEditingEnabled,
            overlayEraserEnabled = overlayEraserEnabled,
            panZoomEnabled = panZoomEnabled,
            overlayVisible = overlayVisible,
            overlayStrokes = overlayStrokes,
            currentOverlayStyle = currentOverlayStyle,
            onOverlayChanged = onOverlayChanged
        )
    }

    fun resetViewport() {
        preview?.resetViewport()
    }

    private fun noPageMessage(): TextView {
        return TextView(context).apply {
            text = "No decoded preview pages are available for this note."
            gravity = Gravity.CENTER
            textSize = 14f
        }
    }
}
