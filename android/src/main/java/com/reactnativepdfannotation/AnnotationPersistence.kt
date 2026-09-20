package com.reactnativepdfannotation

import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

internal object AnnotationPersistence {

    private const val TAG = "AnnotationPersistence"
    const val CURRENT_VERSION = 3

    internal class PointDto(
        @SerializedName("x") var x: Float,
        @SerializedName("y") var y: Float
    ) {
        fun toPointF(): PointF = PointF(x, y)
    }

    internal class StrokeDto {
        @SerializedName("color") var color: String? = null
        @SerializedName("width") var width: Float = 0f
        @SerializedName("timestamp") var timestamp: Long = 0L
        @SerializedName("points") var points: MutableList<PointDto> = ArrayList()

        constructor()

        constructor(s: PdfAnnotationView.AnnotationStroke, viewWidthPx: Float, normalize: Boolean) {
            this.color = String.format("#%08X", s.color)
            this.width = if (normalize && viewWidthPx > 0) s.width / viewWidthPx else s.width
            this.timestamp = s.timestamp
            for (p in s.normalizedPoints) {
                points.add(PointDto(p.x, p.y))
            }
        }

        fun toStroke(pageIndex: Int, viewWidthPx: Float): PdfAnnotationView.AnnotationStroke {
            val c = try {
                Color.parseColor(color)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid color: $color, fallback RED")
                Color.RED
            }
            val px = if (viewWidthPx > 0) width * viewWidthPx else width
            val stroke = PdfAnnotationView.AnnotationStroke(pageIndex, c, px)
            stroke.timestamp = timestamp
            for (pd in points) {
                stroke.normalizedPoints.add(pd.toPointF())
            }
            return stroke
        }
    }

    internal class PageDto {
        @SerializedName("pageIndex") var pageIndex: Int = 0
        @SerializedName("strokes") var strokes: MutableList<StrokeDto> = ArrayList()

        constructor()

        constructor(pageIndex: Int) {
            this.pageIndex = pageIndex
        }
    }

    internal class AnnotationFile {
        @SerializedName("version") var version: Int = CURRENT_VERSION
        @SerializedName("pdfPath") var pdfPath: String? = null
        @SerializedName("savedAt") var savedAt: Long = 0L
        @SerializedName("pages") var pages: MutableList<PageDto> = ArrayList()

        constructor()

        constructor(pdfPath: String) {
            this.pdfPath = pdfPath
            this.savedAt = System.currentTimeMillis()
        }
    }

    private val GSON: Gson = GsonBuilder()
        .serializeSpecialFloatingPointValues()
        .create()

    fun toJson(
        pdfPath: String,
        pageAnnotations: Map<Int, MutableList<PdfAnnotationView.AnnotationStroke>>,
        viewWidthPx: Float
    ): String = serialize(pdfPath, pageAnnotations, viewWidthPx, false)

    fun toJsonNormalized(
        pdfPath: String,
        pageAnnotations: Map<Int, MutableList<PdfAnnotationView.AnnotationStroke>>,
        viewWidthPx: Float
    ): String = serialize(pdfPath, pageAnnotations, viewWidthPx, true)

    private fun serialize(
        pdfPath: String,
        pageAnnotations: Map<Int, MutableList<PdfAnnotationView.AnnotationStroke>>,
        viewWidthPx: Float,
        normalize: Boolean
    ): String {
        val file = AnnotationFile(pdfPath)

        for ((pageIndex, strokes) in pageAnnotations) {
            if (strokes.isEmpty()) continue
            val pageDto = PageDto(pageIndex)
            for (s in strokes) {
                pageDto.strokes.add(StrokeDto(s, viewWidthPx, normalize))
            }
            file.pages.add(pageDto)
        }

        return GSON.toJson(file)
    }

    fun fromJson(
        json: String?,
        viewWidthPx: Float
    ): HashMap<Int, MutableList<PdfAnnotationView.AnnotationStroke>> {
        val result = HashMap<Int, MutableList<PdfAnnotationView.AnnotationStroke>>()
        if (json.isNullOrEmpty()) return result

        try {
            val file = GSON.fromJson(json, AnnotationFile::class.java)
            if (file == null || file.pages == null) return result

            if (file.version > CURRENT_VERSION) {
                Log.w(TAG, "File version ${file.version} > current $CURRENT_VERSION")
            }

            val resolveWidth = if (file.version == 2) viewWidthPx else 0f

            for (page in file.pages) {
                if (page.strokes == null || page.strokes.isEmpty()) continue
                val strokes = ArrayList<PdfAnnotationView.AnnotationStroke>()
                for (dto in page.strokes) {
                    if (dto.points == null || dto.points.size < 2) continue
                    strokes.add(dto.toStroke(page.pageIndex, resolveWidth))
                }
                if (strokes.isNotEmpty()) result[page.pageIndex] = strokes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse annotation JSON", e)
        }

        return result
    }
}
