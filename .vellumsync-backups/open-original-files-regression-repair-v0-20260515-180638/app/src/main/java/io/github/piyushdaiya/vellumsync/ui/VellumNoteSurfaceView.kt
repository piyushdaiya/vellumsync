package io.github.piyushdaiya.vellumsync.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStroke
import io.github.piyushdaiya.vellumsync.note.LocalAnnotationStrokeStyle
import io.github.piyushdaiya.vellumsync.note.SupernoteRenderedPdfReferencePage
import io.github.piyushdaiya.vellumsync.note.SupernoteRenderedVisualPage
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryPageReport

/**
 * XML-backed note surface host.
 *
 * This view replaces the previous plain overlay View in activity_main.xml and
 * keeps the existing Supernote vector preview + local sidecar annotation flow
 * isolated behind a normal Android View API.
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
        renderMode: SupernoteRenderMode,
        renderedVisualPage: SupernoteRenderedVisualPage?,
        renderedPdfReferencePage: SupernoteRenderedPdfReferencePage?,
        overlayEditingEnabled: Boolean,
        overlayEraserEnabled: Boolean,
        panZoomEnabled: Boolean,
        overlayVisible: Boolean,
        overlayStrokes: List<LocalAnnotationStroke>,
        currentOverlayStyle: LocalAnnotationStrokeStyle,
        onOverlayChanged: (List<LocalAnnotationStroke>) -> Unit
    ) {
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
            renderMode = renderMode,
            renderedVisualPage = renderedVisualPage,
            renderedPdfReferencePage = renderedPdfReferencePage,
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
            renderMode = renderMode,
            renderedVisualPage = renderedVisualPage,
            renderedPdfReferencePage = renderedPdfReferencePage,
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
