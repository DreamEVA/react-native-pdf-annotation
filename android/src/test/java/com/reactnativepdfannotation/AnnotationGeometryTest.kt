package com.reactnativepdfannotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationGeometryTest {

    private val pages = listOf(
        AnnotationGeometry.PageSize(100f, 200f),
        AnnotationGeometry.PageSize(100f, 200f)
    )

    @Test
    fun mapsScreenPointOntoFirstPage() {
        val hit = AnnotationGeometry.screenToNormalized(
            screenX = 50f,
            screenY = 50f,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f,
            pageSizes = pages,
            currentPage = 0
        )

        assertEquals(0, hit!!.pageIndex)
        assertEquals(0.5f, hit.x, 0.0001f)
        assertEquals(0.25f, hit.y, 0.0001f)
    }

    @Test
    fun mapsScreenPointOntoSecondPage() {
        val hit = AnnotationGeometry.screenToNormalized(
            screenX = 50f,
            screenY = 300f,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f,
            pageSizes = pages,
            currentPage = 0
        )

        assertEquals(1, hit!!.pageIndex)
        assertEquals(0.5f, hit.x, 0.0001f)
        assertEquals(0.5f, hit.y, 0.0001f)
    }

    @Test
    fun roundTripsThroughZoomAndScroll() {
        val screen = AnnotationGeometry.normalizedToScreen(
            pageIndex = 0,
            normalizedX = 0.25f,
            normalizedY = 0.5f,
            zoom = 2f,
            offsetX = 10f,
            offsetY = 20f,
            pageSizes = pages
        )
        val hit = AnnotationGeometry.screenToNormalized(
            screenX = screen!!.x,
            screenY = screen.y,
            zoom = 2f,
            offsetX = 10f,
            offsetY = 20f,
            pageSizes = pages,
            currentPage = 0
        )

        assertEquals(0, hit!!.pageIndex)
        assertEquals(0.25f, hit.x, 0.0001f)
        assertEquals(0.5f, hit.y, 0.0001f)
    }

    @Test
    fun dropsPointOutsidePageMargin() {
        val hit = AnnotationGeometry.screenToNormalized(
            screenX = -10f,
            screenY = 50f,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f,
            pageSizes = pages,
            currentPage = 0
        )

        assertNull(hit)
    }

    @Test
    fun clampsPointInsideMargin() {
        val hit = AnnotationGeometry.screenToNormalized(
            screenX = -2f,
            screenY = 50f,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f,
            pageSizes = pages,
            currentPage = 0
        )

        assertEquals(0f, hit!!.x, 0.0001f)
    }
}
