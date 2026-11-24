package com.numandegirmenci.belgeizi.data

import java.util.UUID

fun newId() = UUID.randomUUID().toString()

/** Image coordinates are normalized to [0,1], independent of display scale. */
data class OcrLine(
    val id: String = newId(),
    val original: String,
    val text: String = original,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val confidence: Float = 0f
) {
    val edited: Boolean get() = text != original
}

enum class MarkType { SIGNATURE, IMAGE_STAMP, TEXT_STAMP }

data class PageMark(
    val id: String = newId(),
    val type: MarkType,
    val assetPath: String? = null,
    val text: String = "",
    val x: Float = 0.28f,
    val y: Float = 0.70f,
    val w: Float = 0.42f,
    val h: Float = 0.10f
)

data class DocumentPage(
    val path: String,
    val width: Int,
    val height: Int,
    val ocr: List<OcrLine> = emptyList(),
    val ocrDone: Boolean = false,
    val sourcePath: String? = null,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0,
    val enhanceMode: String = "AUTO",
    val ocrVersion: Int = 0,
    val marks: List<PageMark> = emptyList()
)

data class ScanDocument(
    val id: String = newId(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val pages: List<DocumentPage> = emptyList()
)
