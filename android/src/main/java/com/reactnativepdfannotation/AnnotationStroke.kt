package com.reactnativepdfannotation

import android.graphics.PointF

class AnnotationStroke(
    val pageIndex: Int,
    val color: Int,
    val width: Float
) {
    val normalizedPoints = ArrayList<PointF>()
    var timestamp: Long = System.currentTimeMillis()
}
