package com.reactnativepdfannotation

import android.graphics.Color
import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AnnotationPersistenceTest {

    @Test
    fun diskJsonKeepsPixelWidth() {
        val loaded = AnnotationPersistence.fromJson(diskJson(width = 10f), viewWidthPx = 200f)

        val stroke = loaded.getValue(0).single()
        assertEquals(10f, stroke.width, 0.0001f)
        assertEquals(0.1f, stroke.normalizedPoints[0].x, 0.0001f)
        assertEquals(0.2f, stroke.normalizedPoints[0].y, 0.0001f)
    }

    @Test
    fun normalizedJsonStoresWidthRelativeToView() {
        val json = AnnotationPersistence.toJsonNormalized(
            "/a.pdf",
            mapOf(0 to mutableListOf(stroke(width = 10f))),
            viewWidthPx = 200f
        )
        val loaded = AnnotationPersistence.fromJson(json, viewWidthPx = 200f)

        assertEquals(0.05f, loaded.getValue(0).single().width, 0.0001f)
    }

    @Test
    fun version2WidthIsScaledByViewWidth() {
        val loaded = AnnotationPersistence.fromJson(versionedJson(version = 2, width = 0.05f), viewWidthPx = 200f)

        assertEquals(10f, loaded.getValue(1).single().width, 0.0001f)
    }

    @Test
    fun version1WidthIsNotScaled() {
        val loaded = AnnotationPersistence.fromJson(versionedJson(version = 1, width = 5f), viewWidthPx = 200f)

        assertEquals(5f, loaded.getValue(1).single().width, 0.0001f)
    }

    @Test
    fun corruptJsonReturnsEmpty() {
        assertTrue(AnnotationPersistence.fromJson("{", 100f).isEmpty())
        assertTrue(AnnotationPersistence.fromJson("[]", 100f).isEmpty())
        assertTrue(AnnotationPersistence.fromJson(null, 100f).isEmpty())
        assertTrue(AnnotationPersistence.fromJson("", 100f).isEmpty())
    }

    @Test
    fun strokeWithFewerThanTwoPointsIsDropped() {
        val json = """
            {"version":3,"pdfPath":"/a.pdf","pages":[
              {"pageIndex":0,"strokes":[
                {"color":"#FFFF0000","width":5,"timestamp":1,"points":[{"x":0.1,"y":0.2}]}
              ]}
            ]}
        """.trimIndent()

        assertTrue(AnnotationPersistence.fromJson(json, 100f).isEmpty())
    }

    @Test
    fun invalidColorFallsBackToRed() {
        val json = versionedJson(version = 3, width = 5f).replace("#FFFF0000", "not-a-color")
        val loaded = AnnotationPersistence.fromJson(json, viewWidthPx = 100f)

        assertEquals(Color.RED, loaded.getValue(1).single().color)
    }

    private fun stroke(width: Float): AnnotationStroke {
        val stroke = AnnotationStroke(0, Color.RED, width)
        stroke.timestamp = 10L
        stroke.normalizedPoints.add(PointF(0.1f, 0.2f))
        stroke.normalizedPoints.add(PointF(0.3f, 0.4f))
        return stroke
    }

    private fun diskJson(width: Float): String {
        return AnnotationPersistence.toJson(
            "/a.pdf",
            mapOf(0 to mutableListOf(stroke(width))),
            viewWidthPx = 200f
        )
    }

    private fun versionedJson(version: Int, width: Float): String {
        return """
            {"version":$version,"pdfPath":"/a.pdf","savedAt":1,"pages":[
              {"pageIndex":1,"strokes":[
                {"color":"#FFFF0000","width":$width,"timestamp":9,"points":[{"x":0.2,"y":0.3},{"x":0.4,"y":0.5}]}
              ]}
            ]}
        """.trimIndent()
    }
}
