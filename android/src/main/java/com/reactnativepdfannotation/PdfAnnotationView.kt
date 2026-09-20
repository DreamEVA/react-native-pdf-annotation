package com.reactnativepdfannotation

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.facebook.react.uimanager.events.RCTModernEventEmitter
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnPageScrollListener
import com.github.barteksc.pdfviewer.listener.OnRenderListener
import com.github.barteksc.pdfviewer.util.Constants
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.ReaderProperties
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState
import com.shockwave.pdfium.PdfDocument.Bookmark
import com.shockwave.pdfium.util.SizeF
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

open class PdfAnnotationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val TAG = "PdfAnnotationView"
        private const val MAX_HISTORY = 100
        private const val SAVE_DEBOUNCE_MS = 300L

        private val IO_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor { r ->
            Thread(r, "annotation-io").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
    }

    private val reactContext: ThemedReactContext? = context as? ThemedReactContext

    private var pdfView: PDFView? = null
    private var annotationOverlay: AnnotationOverlay? = null
    private var isAnnotationMode = false
    private var isPaused = false
    private var savedZoom = 0f

    private val pageAnnotations = HashMap<Int, MutableList<AnnotationStroke>>()

    private var currentPage = 0
    private var totalPages = 0

    private var layoutChangeListener: OnLayoutChangeListener? = null

    private var strokeColor = Color.RED
    private var strokeWidth = 5f

    private var minScale = 1.0f
    private var maxScale = 5.0f
    private var initialScale = 1.0f

    private var currentStroke: AnnotationStroke? = null

    private val undoStack = ArrayDeque<AnnotationStroke>()
    private val redoStack = ArrayDeque<AnnotationStroke>()

    private var currentPdfPath: String? = null
    private var originalPdfPath: String? = null
    private val saveHandler = Handler(Looper.getMainLooper())
    private var pendingSaveTask: Runnable? = null

    init {
        Constants.Cache.CACHE_SIZE = 120

        val pdf = createPdfView(context)
        pdfView = pdf
        addView(pdf, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val overlay = AnnotationOverlay(context)
        annotationOverlay = overlay
        addView(overlay, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun createPdfView(context: Context): PDFView {
        val view = SafePdfView(context)
        view.useBestQuality(false)
        view.enableAntialiasing(true)
        return view
    }

    private fun recreatePdfView() {
        releasePdfView("recreatePdfView", false)
        val view = createPdfView(context)
        pdfView = view
        addView(view, 0, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun isPdfViewReady(): Boolean {
        val view = pdfView
        return !isPaused && view != null && !view.isRecycled
    }

    private fun safeInvalidateOverlay() {
        val overlay = annotationOverlay
        if (!isPaused && overlay != null && overlay.isAttachedToWindow) {
            overlay.invalidate()
        }
    }

    private fun getPdfViewWidthOrDefault(): Float {
        val view = pdfView
        return if (view != null && view.width > 0) view.width.toFloat() else 1080f
    }

    private fun ensurePdfViewHasSize(targetView: PDFView, source: String): Boolean {
        if (targetView.width > 0 && targetView.height > 0) return true

        val parentWidth = width
        val parentHeight = height
        if (parentWidth <= 0 || parentHeight <= 0) return false

        val widthSpec = MeasureSpec.makeMeasureSpec(parentWidth, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(parentHeight, MeasureSpec.EXACTLY)
        targetView.measure(widthSpec, heightSpec)
        targetView.layout(0, 0, parentWidth, parentHeight)
        Log.i(TAG, "$source: forced PDFView layout, size=${targetView.width}x${targetView.height}, parent=$parentWidth x $parentHeight")
        return targetView.width > 0 && targetView.height > 0
    }

    private fun runWhenPdfViewMeasured(targetView: PDFView, source: String, action: Runnable) {
        if (isPaused || pdfView !== targetView) return

        if (ensurePdfViewHasSize(targetView, source)) {
            action.run()
            return
        }

        Log.i(TAG, "$source: waiting for PDFView layout, size=${targetView.width}x${targetView.height}")
        var executed = false
        var retryCount = 0
        val listenerRef = arrayOfNulls<OnLayoutChangeListener>(1)

        val executeOnce = Runnable {
            if (executed) return@Runnable
            executed = true
            listenerRef[0]?.let { targetView.removeOnLayoutChangeListener(it) }
            action.run()
        }

        val listener = object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                if (executed) return
                if (isPaused || pdfView !== targetView) {
                    targetView.removeOnLayoutChangeListener(this)
                    return
                }
                if ((right - left) > 0 && (bottom - top) > 0) {
                    Log.i(TAG, "$source: PDFView layout ready, size=${right - left}x${bottom - top}")
                    executeOnce.run()
                }
            }
        }
        listenerRef[0] = listener
        targetView.addOnLayoutChangeListener(listener)

        val retry = object : Runnable {
            override fun run() {
                if (executed) return
                if (isPaused || pdfView !== targetView) {
                    targetView.removeOnLayoutChangeListener(listener)
                    return
                }
                if (ensurePdfViewHasSize(targetView, source)) {
                    Log.i(TAG, "$source: PDFView layout ready after retry, size=${targetView.width}x${targetView.height}")
                    executeOnce.run()
                    return
                }
                retryCount++
                if (retryCount <= 20) {
                    requestLayout()
                    targetView.requestLayout()
                    targetView.postDelayed(this, 50)
                } else {
                    Log.w(TAG, "$source: PDFView layout wait timeout, skip loading with size=${targetView.width}x${targetView.height}, parent=${width}x$height")
                }
            }
        }
        targetView.post(retry)
    }

    private fun releasePdfView(reason: String, saveZoomSnapshot: Boolean) {
        val viewToRelease = pdfView ?: return

        layoutChangeListener?.let { viewToRelease.removeOnLayoutChangeListener(it) }
        layoutChangeListener = null

        if (saveZoomSnapshot && !viewToRelease.isRecycled) {
            savedZoom = viewToRelease.zoom
        }
        viewToRelease.stopFling()
        if (!viewToRelease.isRecycled) {
            viewToRelease.recycle()
        }
        removeView(viewToRelease)
        pdfView = null
        Log.i(TAG, "$reason: PDFView released, page=$currentPage/$totalPages, savedZoom=$savedZoom")
    }

    override fun onDetachedFromWindow() {
        saveHandler.removeCallbacksAndMessages(null)
        pendingSaveTask = null

        super.onDetachedFromWindow()
        Log.i(TAG, "onDetachedFromWindow: recycling, page=$currentPage/$totalPages")

        releasePdfView("onDetachedFromWindow", false)
    }
}
