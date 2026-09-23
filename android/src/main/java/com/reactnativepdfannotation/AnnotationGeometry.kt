package com.reactnativepdfannotation

object AnnotationGeometry {

    class PageSize(val width: Float, val height: Float)

    class Hit(val pageIndex: Int, val x: Float, val y: Float)

    fun pageOffsetY(pageIndex: Int, pageHeights: List<Float>): Float {
        var offset = 0f
        var i = 0
        val limit = minOf(pageIndex, pageHeights.size)
        while (i < limit) {
            offset += pageHeights[i]
            i++
        }
        return offset
    }

    fun screenToNormalized(
        screenX: Float,
        screenY: Float,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        pageSizes: List<PageSize>,
        currentPage: Int
    ): Hit? {
        if (pageSizes.isEmpty() || zoom <= 0f) return null

        val docX = (screenX - offsetX) / zoom
        val docY = (screenY - offsetY) / zoom

        var targetPage = currentPage
        var pageStartY = 0f
        for (i in pageSizes.indices) {
            val pageHeight = pageSizes[i].height
            if (docY >= pageStartY && docY < pageStartY + pageHeight) {
                targetPage = i
                break
            }
            pageStartY += pageHeight
        }

        if (targetPage < 0 || targetPage >= pageSizes.size) return null

        val targetSize = pageSizes[targetPage]
        val targetPageStartY = pageOffsetY(targetPage, pageSizes.map { it.height })

        var normalizedX = docX / targetSize.width
        var normalizedY = (docY - targetPageStartY) / targetSize.height

        if (normalizedX < -0.05f || normalizedX > 1.05f ||
            normalizedY < -0.05f || normalizedY > 1.05f) {
            return null
        }

        normalizedX = normalizedX.coerceIn(0f, 1f)
        normalizedY = normalizedY.coerceIn(0f, 1f)
        return Hit(targetPage, normalizedX, normalizedY)
    }

    fun normalizedToScreen(
        pageIndex: Int,
        normalizedX: Float,
        normalizedY: Float,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        pageSizes: List<PageSize>
    ): Hit? {
        if (pageIndex < 0 || pageIndex >= pageSizes.size) return null

        val pageSize = pageSizes[pageIndex]
        val pageStartY = pageOffsetY(pageIndex, pageSizes.map { it.height })
        val docX = normalizedX * pageSize.width
        val docY = pageStartY + normalizedY * pageSize.height
        val screenX = docX * zoom + offsetX
        val screenY = docY * zoom + offsetY
        return Hit(pageIndex, screenX, screenY)
    }
}
