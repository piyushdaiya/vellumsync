package io.github.piyushdaiya.vellumsync.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.piyushdaiya.vellumsync.note.ImportedNoteCache
import io.github.piyushdaiya.vellumsync.note.MarkerHit
import io.github.piyushdaiya.vellumsync.note.SupernoteContainerReport
import io.github.piyushdaiya.vellumsync.note.SupernoteInspectionReport
import io.github.piyushdaiya.vellumsync.note.SupernoteVisualReport
import io.github.piyushdaiya.vellumsync.note.SupernoteTotalPathProbeReport
import io.github.piyushdaiya.vellumsync.note.SupernoteStrokeGeometryReport
import io.github.piyushdaiya.vellumsync.note.SupernoteNoteInspector

@Composable
fun NoteInspectorScreen(
    onBack: () -> Unit,
    onOpenViewer: (ViewerNoteSelection) -> Unit = {}
) {
    val context = LocalContext.current
    val report = remember { mutableStateOf<SupernoteInspectionReport?>(null) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val exportError = remember { mutableStateOf<String?>(null) }
    val pendingExportJson = remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                writeTextToUri(
                    context = context,
                    uri = uri,
                    text = pendingExportJson.value.orEmpty()
                )
            }.onFailure { throwable ->
                exportError.value = throwable.message ?: "Unable to export diagnostics JSON."
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        runCatching {
            val fileName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "selected.note"
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: error("Unable to read selected file.")

            val preliminaryReport = SupernoteNoteInspector.inspect(
                fileName = fileName,
                fileSizeBytes = bytes.size.toLong(),
                bytes = bytes
            )
            val cacheResult = ImportedNoteCache.cacheReadOnlyCopy(
                context = context,
                fileName = fileName,
                bytes = bytes,
                preliminaryReport = preliminaryReport
            )

            SupernoteNoteInspector.inspect(
                fileName = fileName,
                fileSizeBytes = bytes.size.toLong(),
                bytes = bytes,
                cachedCopyPath = cacheResult.cacheFile.absolutePath
            )
        }.onSuccess { inspection ->
            report.value = inspection
            errorMessage.value = null
            exportError.value = null
        }.onFailure { throwable ->
            errorMessage.value = throwable.message ?: "Unknown error while reading file."
        }
    }

    Column(
        modifier = Modifier
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Supernote .note diagnostics")

        Text(
            text = "Diagnostics are secondary to Viewer Mode. This screen performs read-only marker, parser, and geometry inspection. It does not modify the selected file."
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                onClick = {
                    picker.launch(
                        arrayOf(
                            "application/octet-stream",
                            "application/x-note",
                            "*/*"
                        )
                    )
                }
            ) {
                Text(text = "Select .note file")
            }

            Button(onClick = onBack) {
                Text(text = "Back to device check")
            }
        }

        report.value?.let { inspection ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    onClick = {
                        val cachedPath = inspection.cachedCopyPath
                        if (cachedPath == null) {
                            exportError.value = "This note has no cached read-only copy to open. Re-import it first."
                        } else {
                            onOpenViewer(
                                ViewerNoteSelection(
                                    fileName = inspection.fileName,
                                    sha256 = inspection.sha256,
                                    notePath = cachedPath,
                                    fileSizeBytes = inspection.fileSizeBytes
                                )
                            )
                        }
                    }
                ) {
                    Text(text = "Open in viewer")
                }

                Button(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    onClick = {
                        pendingExportJson.value = inspection.toJson()
                        exportLauncher.launch("vellumsync-note-diagnostics-${inspection.sha256.take(12)}.json")
                    }
                ) {
                    Text(text = "Export diagnostics JSON")
                }
            }
        }

        errorMessage.value?.let { error ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "Error: $error"
                )
            }
        }

        exportError.value?.let { error ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "Export error: $error"
                )
            }
        }

        report.value?.let { inspection ->
            InspectionReportCard(report = inspection)
            ContainerParserReportCard(report = inspection.containerReport)
            VisualDecoderReportCard(report = inspection.visualReport)
            TotalPathProbeReportCard(report = inspection.totalPathProbeReport)
            StrokeGeometryReportCard(report = inspection.strokeGeometryReport)
            MarkerReportCard(markerHits = inspection.markerHits)
        }
    }
}

@Composable
private fun InspectionReportCard(report: SupernoteInspectionReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = "File: ${report.fileName}")
            Text(text = "Size: ${report.fileSizeBytes} bytes")
            Text(text = "SHA-256: ${report.sha256}")
            Text(text = "Header hex: ${report.headerPreviewHex}")
            Text(text = "Header ASCII: ${report.headerPreviewAscii}")
            Text(text = "Version: ${report.versionMarker ?: "not detected"}")
            Text(text = "Equipment: ${report.detectedEquipment ?: "not detected"}")
            Text(text = "Estimated pages: ${report.estimatedPageCount}")
            Text(text = "NOTE marker: ${report.hasNoteMarker}")
            Text(text = "MAINLAYER: ${report.hasMainLayer}")
            Text(text = "BGLAYER: ${report.hasBackgroundLayer}")
            Text(text = "LAYERINFO: ${report.hasLayerInfo}")
            Text(text = "LAYERSEQ: ${report.hasLayerSequence}")
            Text(text = "TOTALPATH: ${report.hasTotalPath}")
            Text(text = "PAGESTYLE: ${report.hasPageStyle}")
            Text(text = "TITLE metadata: ${report.hasTitleMetadata}")
            Text(text = "KEYWORD metadata: ${report.hasKeywordMetadata}")
            Text(text = "Real LINK metadata: ${report.hasLinkMetadata}")
            Text(text = "EXTERNALLINKINFO field: ${report.hasExternalLinkInfoField}")
            Text(text = "STAR metadata: ${report.hasStarMetadata}")
            Text(text = "Cached copy: ${report.cachedCopyPath ?: "not cached"}")
            Text(text = "Status: ${report.compatibilityStatus}")

            if (report.warnings.isNotEmpty()) {
                Text(text = "Warnings")
                report.warnings.forEach { warning ->
                    Text(text = "• $warning")
                }
            }
        }
    }
}

@Composable
private fun ContainerParserReportCard(report: SupernoteContainerReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Structured container parser v0")
            Text(text = "Version: ${report.header.versionMarker ?: "not parsed"}")
            Text(text = "Module label: ${report.header.moduleLabel ?: "not parsed"}")
            Text(text = "File type: ${report.header.fileType ?: "not parsed"}")
            Text(text = "Apply equipment: ${report.header.applyEquipment ?: "not parsed"}")
            Text(text = "Final operation page: ${report.header.finalOperationPage ?: "not parsed"}")
            Text(text = "Final operation layer: ${report.header.finalOperationLayer ?: "not parsed"}")
            Text(text = "File ID: ${report.header.fileId ?: "not parsed"}")
            Text(text = "Page table count: ${report.pageCount}")
            Text(text = "Real link metadata: ${report.realLinkMetadataPresent}")
            Text(text = "EXTERNALLINKINFO field: ${report.externalLinkInfoPresent}")

            if (report.parserWarnings.isNotEmpty()) {
                Text(text = "Parser warnings")
                report.parserWarnings.forEach { warning ->
                    Text(text = "• $warning")
                }
            }

            Text(text = "Page table")
            report.pageReferences.forEach { page ->
                Text(text = "PAGE${page.pageNumber}: section offset=${page.pageSectionOffset}")
            }

            Text(text = "Page section index")
            report.pageSections.forEach { page ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = "Page ${page.pageNumber}")
                    Text(text = "style=${page.pageStyle ?: "not parsed"}")
                    Text(text = "layerSeq=${page.layerSeq ?: "not parsed"}")
                    Text(text = "layerInfoOffset=${page.layerInfoOffset ?: "not parsed"}")
                    Text(text = "MAINLAYER=${page.layerOffsets.mainLayerOffset ?: "not parsed"}")
                    Text(text = "BGLAYER=${page.layerOffsets.backgroundLayerOffset ?: "not parsed"}")
                    Text(text = "LAYER1=${page.layerOffsets.layer1Offset ?: "not parsed"}")
                    Text(text = "LAYER2=${page.layerOffsets.layer2Offset ?: "not parsed"}")
                    Text(text = "LAYER3=${page.layerOffsets.layer3Offset ?: "not parsed"}")
                    Text(text = "TOTALPATH=${page.layerOffsets.totalPathOffset ?: "not parsed"}")
                    Text(text = "realLink=${page.realLinkMetadataPresent} externalLinkField=${page.externalLinkInfoPresent}")
                }
            }
        }
    }
}

@Composable
private fun VisualDecoderReportCard(report: SupernoteVisualReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Supernote visual pointer + boundary probe v0")
            Text(text = "Status: ${report.formatStatus}")
            Text(text = "Layer records: ${report.totalLayerRecords}")
            Text(text = "RATTA_RLE records: ${report.rleLayerRecordCount}")
            Text(text = "Unique bitmap payload offsets: ${report.uniqueBitmapPayloadOffsetCount}")

            if (report.sharedBitmapPayloads.isNotEmpty()) {
                Text(text = "Shared bitmap payloads")
                report.sharedBitmapPayloads.forEach { payload ->
                    Text(text = "offset=${payload.bitmapPayloadOffset} reuse=${payload.reuseCount}")
                    payload.usedBy.forEach { usedBy ->
                        Text(text = "• $usedBy")
                    }
                }
            }

            if (report.decoderWarnings.isNotEmpty()) {
                Text(text = "Visual decoder warnings")
                report.decoderWarnings.forEach { warning ->
                    Text(text = "• $warning")
                }
            }

            Text(text = "Payload boundary probe")
            Text(text = "Bitmap rendering is intentionally deferred. This view treats LAYERBITMAP as a payload offset and estimates conservative compressed-payload boundaries.")

            report.pageReports.forEach { page ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Page ${page.pageNumber}: ${page.previewStatus}")
                    Text(text = "style=${page.pageStyle ?: "not parsed"}")
                    Text(text = "layerSeq=${page.layerSeq ?: "not parsed"}")
                    if (page.warnings.isNotEmpty()) {
                        page.warnings.forEach { warning ->
                            Text(text = "• $warning")
                        }
                    }
                    page.layerRecords.forEach { layer ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = "${layer.logicalLayerName} recordOffset=${layer.layerRecordOffset} nextStruct=${layer.nextKnownStructuralOffset ?: "unknown"}")
                            Text(text = "type=${layer.layerType ?: "?"} protocol=${layer.layerProtocol ?: "?"} name=${layer.parsedLayerName ?: "?"}")
                            Text(text = "metadataBytes=${layer.metadataRecordByteLength ?: "?"} bitmapOffset=${layer.bitmapPayloadOffset ?: "?"}")
                            Text(text = "payloadEnd=${layer.estimatedCompressedPayloadEndOffset ?: "?"} payloadBytes=${layer.estimatedCompressedPayloadByteLength ?: "?"}")
                            Text(text = "startsBeforeRecord=${layer.bitmapPayloadStartsBeforeLayerRecord ?: "?"} shared=${layer.bitmapPayloadShared} reuse=${layer.bitmapPayloadReuseCount}")
                            Text(text = "status=${layer.bitmapPayloadStatus}")
                            Text(text = "preview=${layer.recordPreviewAscii}")
                            layer.warnings.forEach { warning ->
                                Text(text = "• $warning")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalPathProbeReportCard(report: SupernoteTotalPathProbeReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "TOTALPATH record-chain + point-array decoder v0")
            Text(text = "Status: ${report.formatStatus}")
            Text(text = "Pages with TOTALPATH: ${report.pagesWithTotalPath}")
            Text(text = "Total estimated payload bytes: ${report.totalEstimatedPayloadBytes}")

            if (report.probeWarnings.isNotEmpty()) {
                Text(text = "Probe warnings")
                report.probeWarnings.forEach { warning ->
                    Text(text = "• $warning")
                }
            }

            report.pageReports.forEach { page ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Page ${page.pageNumber}")
                    Text(text = "TOTALPATH offset=${page.totalPathOffset ?: "not parsed"} pageSectionOffset=${page.pageSectionOffset}")
                    Text(text = "payloadStart=${page.estimatedPayloadStartOffset ?: "?"} payloadEnd=${page.estimatedPayloadEndOffset ?: "?"} payloadBytes=${page.estimatedPayloadByteLength ?: "?"}")
                    Text(text = "declaredPayloadSize=${page.declaredPayloadSize ?: "unknown"} declaredRecordCount=${page.declaredRecordCount ?: "unknown"}")
                    Text(text = "headerSizeMatchesPayload=${page.headerSizeMatchesPayload ?: "unknown"} semanticRecordMarkers=${page.semanticRecordMarkerCount} recordCountMatchesMarkers=${page.recordCountMatchesSemanticMarkers ?: "unknown"}")
                    Text(text = "candidateStrokeRecordCount=${page.candidateStrokeRecordCount ?: "unknown"}")
                    Text(text = "recordsDecodedByLengthChain=${page.recordsDecodedByLengthChain} recordsWithPointArrays=${page.recordsWithDecodedPointArrays}")
                    Text(text = "boundaryStatus=${page.recordBoundaryModelStatus}")
                    Text(text = "chainStatus=${page.recordChainDecoderStatus}")
                    Text(text = "pointArrayStatus=${page.pointArrayDecodeStatus}")
                    if (page.candidateRecords.isNotEmpty()) {
                        Text(text = "Candidate record boundaries")
                        page.candidateRecords.take(12).forEach { record ->
                            Text(text = "#${record.recordIndex} ${record.category} start=${record.estimatedRecordStartRelativeOffset}/${record.estimatedRecordStartAbsoluteOffset} end=${record.estimatedRecordEndRelativeOffset}/${record.estimatedRecordEndAbsoluteOffset} bytes=${record.estimatedRecordByteLength}")
                            Text(text = "declaredRecordPayloadSize=${record.declaredRecordPayloadSize ?: "?"} source=${record.recordLengthSource} chain=${record.decodedByLengthChain}")
                            Text(text = "firstU32=${record.firstU32LeFields.take(8).joinToString()}")
                            record.candidateBounds?.let { bounds ->
                                Text(text = "boundsProbe rel=${bounds.sourceRelativeOffset} values=${bounds.values.joinToString()}")
                            }
                            record.candidatePointRun?.let { run ->
                                Text(text = "pointRun ${run.encoding} relPayload=${run.relativeOffsetInPayload} relRecord=${run.relativeOffsetInRecord} pairs=${run.pairCount}")
                                Text(text = "pointPreview=${run.previewPairs.joinToString { pair -> "[${pair.joinToString()}]" }}")
                            }
                            record.decodedPointArray?.let { decoded ->
                                Text(text = "decodedPointArray count=${decoded.decodedPointCount} fieldRel=${decoded.pointCountFieldRelativeOffsetInRecord} arrayRel=${decoded.pointArrayRelativeOffsetInRecord}")
                                Text(text = "rawBounds x=${decoded.minX}..${decoded.maxX} y=${decoded.minY}..${decoded.maxY}")
                                Text(text = "rawPreview=${decoded.rawPointPreview.joinToString { point -> "[${point.x}, ${point.y}]" }}")
                            }
                            record.warnings.forEach { warning -> Text(text = "  • $warning") }
                        }
                    }
                    page.binarySummary?.let { summary ->
                        Text(text = "binarySummary zero=${summary.zeroByteCount} printable=${summary.printableAsciiByteCount} distinct=${summary.distinctByteCount} likelyBinary=${summary.likelyBinary}")
                    }
                    Text(text = "first128Hex=${page.firstPreviewHex}")
                    Text(text = "first128Ascii=${page.firstPreviewAscii}")

                    if (page.candidateStrokeRecordSignals.isNotEmpty()) {
                        Text(text = "Candidate stroke signals")
                        page.candidateStrokeRecordSignals.forEach { signal ->
                            Text(text = "• $signal")
                        }
                    }

                    if (page.candidateToolSignals.isNotEmpty()) {
                        Text(text = "Candidate tool/semantic signals")
                        page.candidateToolSignals.forEach { signal ->
                            Text(text = "• $signal")
                        }
                    }

                    val markerHits = page.markerHits.filter { it.count > 0 }
                    if (markerHits.isNotEmpty()) {
                        Text(text = "TOTALPATH marker hits")
                        markerHits.forEach { hit ->
                            Text(text = "${hit.marker}: count=${hit.count} rel=${hit.relativeOffsets.joinToString()} abs=${hit.absoluteOffsets.joinToString()}")
                            hit.contexts.forEachIndexed { index, context ->
                                Text(text = "context ${index + 1}: $context")
                            }
                        }
                    }

                    if (page.numericRuns.isNotEmpty()) {
                        Text(text = "Numeric run probes")
                        page.numericRuns.forEach { run ->
                            Text(text = "${run.encoding} abs=${run.absoluteOffset} rel=${run.relativeOffset} count=${run.valueCount} range=${run.minValue}..${run.maxValue}")
                            Text(text = "values=${run.previewValues.joinToString()}")
                            Text(text = "reason=${run.reason}")
                        }
                    }

                    page.warnings.forEach { warning ->
                        Text(text = "• $warning")
                    }
                }
            }
        }
    }
}

@Composable
private fun StrokeGeometryReportCard(report: SupernoteStrokeGeometryReport) {
    val selectedPageIndex = remember { mutableStateOf(0) }
    val selectedTransformMode = remember {
        mutableStateOf(SupernotePreviewTransformMode.fromId(report.defaultTransformMode))
    }
    val pages = report.pageReports
    val selectedPage = pages.getOrNull(selectedPageIndex.value.coerceIn(0, (pages.size - 1).coerceAtLeast(0)))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Render fidelity + page preview v0")
            Text(text = "Status: ${report.formatStatus}")
            Text(text = "Pages: ${report.totalPages}")
            Text(text = "Decoded records: ${report.totalDecodedRecords}")
            Text(text = "Rendered records: ${report.totalRenderedRecords}")
            Text(text = "Skipped records: ${report.totalSkippedRecords}")
            Text(text = "Unknown subtype records: ${report.totalUnknownSubtypeRecords}")
            Text(text = "Possible eraser/metadata records: ${report.totalPossibleEraserOrMetadataRecords}")
            Text(text = "Default transform: ${report.defaultTransformMode}")
            Text(text = "Selected transform: ${selectedTransformMode.value.label}")

            if (report.warnings.isNotEmpty()) {
                Text(text = "Render fidelity warnings")
                report.warnings.forEach { warning -> Text(text = "• $warning") }
            }

            if (pages.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        selectedPageIndex.value = (selectedPageIndex.value - 1).coerceAtLeast(0)
                    }) {
                        Text(text = "Prev page")
                    }
                    Button(onClick = {
                        selectedPageIndex.value = (selectedPageIndex.value + 1).coerceAtMost(pages.lastIndex)
                    }) {
                        Text(text = "Next page")
                    }
                }

                Text(text = "Transform candidates")
                SupernotePreviewTransformMode.values().toList().chunked(2).forEach { rowModes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowModes.forEach { mode ->
                            Button(onClick = { selectedTransformMode.value = mode }) {
                                Text(text = mode.label)
                            }
                        }
                    }
                }

                selectedPage?.let { page ->
                    Text(text = "Preview page ${page.pageNumber}")
                    Text(text = "Records rendered=${page.renderedRecords}/${page.decodedRecords} skipped=${page.skippedRecords}")
                    Text(text = "Unknown=${page.unknownSubtypeRecords} possibleEraserOrMetadata=${page.possibleEraserOrMetadataRecords}")
                    Text(text = "Raw bounds=${page.rawBounds?.minX ?: "?"}..${page.rawBounds?.maxX ?: "?"}, ${page.rawBounds?.minY ?: "?"}..${page.rawBounds?.maxY ?: "?"}")
                    Text(text = "Geometry source transform=${page.transform}")
                    Text(text = "Preview transform=${selectedTransformMode.value.id}")
                    Text(text = "Background: ruled placeholder until RATTA_RLE BGLAYER decode is enabled")

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(640.dp),
                        factory = { context ->
                            SupernoteVectorPreviewView(
                                context = context,
                                pageReport = page,
                                transformMode = selectedTransformMode.value
                            )
                        },
                        update = { view ->
                            view.updatePageReport(
                                pageReport = page,
                                transformMode = selectedTransformMode.value
                            )
                        }
                    )

                    if (page.warnings.isNotEmpty()) {
                        Text(text = "Page warnings")
                        page.warnings.forEach { warning -> Text(text = "• $warning") }
                    }

                    Text(text = "Render summary")
                    Text(text = "Page size=${page.pageWidth} x ${page.pageHeight}")
                    Text(text = "Transform mode=${selectedTransformMode.value.label}")
                    Text(text = "Decoded=${page.decodedRecords} Rendered=${page.renderedRecords} Skipped=${page.skippedRecords}")
                    Text(text = "Unknown=${page.unknownSubtypeRecords} Possible eraser/metadata=${page.possibleEraserOrMetadataRecords}")

                    Text(text = "Record summary")
                    page.records.take(16).forEach { record ->
                        Text(text = "#${record.recordIndex} ${record.subtype} source=${record.source} renderedPoints=${record.renderedPointCount} decodedPoints=${record.decodedPointCount}")
                        record.rawBounds?.let { bounds ->
                            Text(text = "  raw=${bounds.minX}..${bounds.maxX}, ${bounds.minY}..${bounds.maxY}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkerReportCard(markerHits: List<MarkerHit>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Marker offsets and context")
            markerHits.forEach { hit ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${hit.marker}: count=${hit.count} offsets=${hit.offsets.joinToString()}"
                    )
                    hit.contexts.forEachIndexed { index, context ->
                        Text(text = "context ${index + 1}: $context")
                    }
                }
            }
        }
    }
}

private fun queryDisplayName(
    context: android.content.Context,
    uri: Uri
): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    }
}
