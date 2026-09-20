package com.reactnativepdfannotation

import android.app.Activity
import android.view.ViewGroup

object PdfAnnotationLifecycle {

    @JvmStatic
    fun onHostPause(activity: Activity) {
        dispatch(activity) { it.onHostPause() }
    }

    @JvmStatic
    fun onHostStop(activity: Activity) {
        dispatch(activity) { it.onHostStop() }
    }

    @JvmStatic
    fun onHostResume(activity: Activity) {
        dispatch(activity) { it.onHostResume() }
    }

    @JvmStatic
    fun onTrimMemory(activity: Activity, level: Int) {
        dispatch(activity) { it.onTrimMemory(level) }
    }

    private fun dispatch(activity: Activity, action: (PdfAnnotationView) -> Unit) {
        val root = activity.window.decorView
        if (root is ViewGroup) {
            findAndDispatch(root, action)
        }
    }

    private fun findAndDispatch(parent: ViewGroup, action: (PdfAnnotationView) -> Unit) {
        for (i in 0 until parent.childCount) {
            when (val child = parent.getChildAt(i)) {
                is PdfAnnotationView -> action(child)
                is ViewGroup -> findAndDispatch(child, action)
            }
        }
    }
}
