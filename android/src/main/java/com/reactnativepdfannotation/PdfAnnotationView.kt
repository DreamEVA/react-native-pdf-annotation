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

    fun onHostPause() {
        isPaused = true
        currentStroke = null
        releasePdfView("onHostPause", true)
        val view = pdfView
        Log.i(TAG, "onHostPause: isPaused=true, savedZoom=$savedZoom, page=$currentPage/$totalPages, recycled=${view == null || view.isRecycled}")
    }

    fun onHostStop() {
        isPaused = true
        currentStroke = null
        releasePdfView("onHostStop", true)
    }

    fun onHostResume() {
        isPaused = false
        val path = currentPdfPath
        if (path != null) {
            reloadPdf(path, currentPage)
        } else {
            annotationOverlay?.invalidate()
        }
    }

    fun onTrimMemory(level: Int) {
        Log.i(TAG, "onTrimMemory: level=$level, isPaused=$isPaused, recycled=${pdfView?.isRecycled ?: true}")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            Log.w(TAG, "onTrimMemory UI_HIDDEN: releasing PDFView, page=$currentPage")
            recyclePdfViewForMemoryPressure()
        }
    }

    private fun recyclePdfViewForMemoryPressure() {
        isPaused = true
        pendingSaveTask?.let { saveHandler.removeCallbacks(it) }
        pendingSaveTask = null
        currentStroke = null
        releasePdfView("recyclePdfViewForMemoryPressure", true)
        Log.w(TAG, "PDF recycled due to memory pressure, will reload on next resume.")
    }

    fun setAnnotationMode(annotationMode: Boolean) {
        isAnnotationMode = annotationMode
        annotationOverlay?.invalidate()
    }

    fun setStrokeColor(color: String) {
        strokeColor = try {
            Color.parseColor(color)
        } catch (e: Exception) {
            Color.RED
        }
    }

    fun setStrokeWidth(width: Float) {
        strokeWidth = width
    }

    fun setMinScale(scale: Float) {
        minScale = scale
    }

    fun setMaxScale(scale: Float) {
        maxScale = scale
    }

    fun setInitialScale(scale: Float) {
        initialScale = scale
        if (totalPages > 0 && isPdfViewReady()) {
            applyStableZoom(scale)
        }
    }

    fun setOriginalPath(originalPath: String?) {
        originalPdfPath = originalPath?.replace("file://", "")
    }

    private fun applyStableZoom(scale: Float) {
        val view = pdfView ?: return
        if (!isPdfViewReady()) return
        view.stopFling()
        view.zoomTo(scale)
        view.loadPages()
        view.invalidate()
    }

    private fun startPdfLoadWhenMeasured(loadingView: PDFView, file: File, defaultPage: Int,
                                         isReload: Boolean, source: String, filePathForEvent: String) {
        runWhenPdfViewMeasured(loadingView, source, Runnable {
            if (isPaused || pdfView !== loadingView) return@Runnable

            loadingView.fromFile(file)
                .defaultPage(defaultPage)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(false)
                .enableAnnotationRendering(true)
                .enableAntialiasing(true)
                .pageFitPolicy(FitPolicy.WIDTH)
                .fitEachPage(true)
                .spacing(0)
                .autoSpacing(false)
                .onPageChange(object : OnPageChangeListener {
                    override fun onPageChanged(page: Int, pageCount: Int) {
                        if (isPaused || pdfView !== loadingView || loadingView.isRecycled) return
                        currentPage = page
                        totalPages = pageCount
                        safeInvalidateOverlay()
                        sendPageChanged(page, pageCount)
                    }
                })
                .onPageScroll(object : OnPageScrollListener {
                    override fun onPageScrolled(page: Int, positionOffset: Float) {
                        if (isPaused || pdfView !== loadingView || loadingView.isRecycled) return
                        safeInvalidateOverlay()
                    }
                })
                .onLoad(object : OnLoadCompleteListener {
                    override fun loadComplete(nbPages: Int) {
                        if (isPaused || pdfView !== loadingView || loadingView.isRecycled) return
                        if (!isReload) {
                            sendTableOfContents(loadingView, filePathForEvent)
                        }
                    }
                })
                .onRender(object : OnRenderListener {
                    override fun onInitiallyRendered(nbPages: Int) {
                        if (isPaused || pdfView !== loadingView || loadingView.isRecycled) return
                        totalPages = nbPages
                        loadingView.setMinZoom(minScale)
                        loadingView.setMidZoom((minScale + maxScale) / 2)
                        loadingView.setMaxZoom(maxScale)

                        val zoomToApply = if (isReload && savedZoom > 0f) savedZoom else initialScale
                        Log.i(TAG, "$source onRender: pages=$nbPages, zoom=$zoomToApply, path=$filePathForEvent")
                        applyStableZoom(zoomToApply)
                        if (!isReload) {
                            loadAnnotationsFromDisk(filePathForEvent)
                            sendLoadComplete(nbPages, filePathForEvent)
                        }
                        safeInvalidateOverlay()
                    }
                })
                .onError(object : OnErrorListener {
                    override fun onError(t: Throwable) {
                        Log.e(TAG, "$source onError: ${t.message}", t)
                        sendError(t.message ?: "Unknown error")
                    }
                })
                .load()
        })
    }

    private fun reloadPdf(filePath: String, restorePage: Int) {
        if (isPaused) return
        Log.i(TAG, "reloadPdf: path=$filePath, restorePage=$restorePage, savedZoom=$savedZoom")
        recreatePdfView()
        val loadingView = pdfView ?: return
        val file = File(filePath)
        startPdfLoadWhenMeasured(loadingView, file, restorePage, true, "reloadPdf", filePath)

        layoutChangeListener?.let { loadingView.removeOnLayoutChangeListener(it) }
        val listener = object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                safeInvalidateOverlay()
                val sizeChanged = (right - left) != (oldRight - oldLeft) ||
                        (bottom - top) != (oldBottom - oldTop)
                if (sizeChanged && totalPages > 0 && pdfView === loadingView && isPdfViewReady()) {
                    applyStableZoom(initialScale)
                }
            }
        }
        layoutChangeListener = listener
        loadingView.addOnLayoutChangeListener(listener)
    }

    fun loadPdf(filePath: String) {
        val normalizedPath = filePath.replace("file://", "")

        currentPdfPath = normalizedPath
        Log.i(TAG, "loadPdf: path=$normalizedPath")

        pageAnnotations.clear()
        undoStack.clear()
        redoStack.clear()

        isPaused = false
        recreatePdfView()
        val loadingView = pdfView ?: return
        val file = File(normalizedPath)

        startPdfLoadWhenMeasured(loadingView, file, 0, false, "loadPdf", normalizedPath)

        layoutChangeListener?.let { loadingView.removeOnLayoutChangeListener(it) }
        val listener = object : OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                safeInvalidateOverlay()
                val sizeChanged = (right - left) != (oldRight - oldLeft) ||
                        (bottom - top) != (oldBottom - oldTop)
                if (sizeChanged && totalPages > 0 && pdfView === loadingView && isPdfViewReady()) {
                    applyStableZoom(initialScale)
                }
            }
        }
        layoutChangeListener = listener
        loadingView.addOnLayoutChangeListener(listener)
    }

    private fun getPageOffsetY(pageIndex: Int): Float {
        val view = pdfView ?: return 0f
        if (!isPdfViewReady()) return 0f
        var offset = 0f
        var i = 0
        while (i < pageIndex && i < totalPages) {
            offset += view.getPageSize(i).height
            i++
        }
        return offset
    }

    private fun screenToNormalized(screenX: Float, screenY: Float, outPageIndex: IntArray?): PointF? {
        val view = pdfView ?: return null
        if (!isPdfViewReady()) return null
        val zoom = view.zoom
        if (totalPages <= 0 || zoom <= 0) return null

        val offsetX = view.currentXOffset
        val offsetY = view.currentYOffset

        val docX = (screenX - offsetX) / zoom
        val docY = (screenY - offsetY) / zoom

        var targetPage = currentPage
        var pageStartY = 0f

        for (i in 0 until totalPages) {
            val pageHeight = view.getPageSize(i).height
            if (docY >= pageStartY && docY < pageStartY + pageHeight) {
                targetPage = i
                break
            }
            pageStartY += pageHeight
        }

        val targetSize = view.getPageSize(targetPage)
        val pageWidth = targetSize.width
        val pageHeight = targetSize.height
        val targetPageStartY = getPageOffsetY(targetPage)

        var normalizedX = docX / pageWidth
        var normalizedY = (docY - targetPageStartY) / pageHeight

        if (normalizedX < -0.05f || normalizedX > 1.05f ||
            normalizedY < -0.05f || normalizedY > 1.05f) {
            return null
        }

        normalizedX = normalizedX.coerceIn(0f, 1f)
        normalizedY = normalizedY.coerceIn(0f, 1f)

        if (outPageIndex != null && outPageIndex.isNotEmpty()) {
            outPageIndex[0] = targetPage
        }

        return PointF(normalizedX, normalizedY)
    }

    private fun normalizedToScreen(pageIndex: Int, normalizedX: Float, normalizedY: Float): PointF? {
        val view = pdfView ?: return null
        if (!isPdfViewReady()) return null
        if (pageIndex < 0 || pageIndex >= totalPages) return null

        val zoom = view.zoom
        val offsetX = view.currentXOffset
        val offsetY = view.currentYOffset

        val pageSize = view.getPageSize(pageIndex)
        val pageWidth = pageSize.width
        val pageHeight = pageSize.height

        val pageStartY = getPageOffsetY(pageIndex)
        val docX = normalizedX * pageWidth
        val docY = pageStartY + normalizedY * pageHeight

        val screenX = docX * zoom + offsetX
        val screenY = docY * zoom + offsetY

        return PointF(screenX, screenY)
    }

    private fun commitStroke(stroke: AnnotationStroke) {
        val list = pageAnnotations.getOrPut(stroke.pageIndex) { ArrayList() }
        list.add(stroke)

        redoStack.clear()

        undoStack.push(stroke)
        if (undoStack.size > MAX_HISTORY) {
            undoStack.pollLast()
        }

        scheduleAutoSave()
        sendAnnotationChanged()
    }

    fun undo() {
        if (undoStack.isEmpty()) return

        val stroke = undoStack.pop()

        val strokes = pageAnnotations[stroke.pageIndex]
        if (strokes != null) {
            for (i in strokes.indices.reversed()) {
                if (strokes[i] === stroke) {
                    strokes.removeAt(i)
                    break
                }
            }
            if (strokes.isEmpty()) {
                pageAnnotations.remove(stroke.pageIndex)
            }
        }

        redoStack.push(stroke)
        annotationOverlay?.invalidate()
        scheduleAutoSave()
        sendAnnotationChanged()
    }

    fun redo() {
        if (redoStack.isEmpty()) return

        val stroke = redoStack.pop()

        val list = pageAnnotations.getOrPut(stroke.pageIndex) { ArrayList() }
        list.add(stroke)

        undoStack.push(stroke)
        annotationOverlay?.invalidate()
        scheduleAutoSave()
        sendAnnotationChanged()
    }

    fun clearAll() {
        pageAnnotations.clear()
        undoStack.clear()
        redoStack.clear()
        annotationOverlay?.invalidate()
        deleteAnnotationFile()
        sendAnnotationChanged()
    }

    fun exportAnnotations(exportDir: String) {
        val path = currentPdfPath
        if (path == null) {
            Log.w(TAG, "No PDF loaded, cannot export")
            sendExportResult(false, "No PDF loaded")
            return
        }

        val snapshot = snapshotAnnotations()
        val viewWidthPx = getPdfViewWidthOrDefault()

        IO_EXECUTOR.submit {
            try {
                val dir = File(exportDir)
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                val json = AnnotationPersistence.toJsonNormalized(path, snapshot, viewWidthPx)
                val fileName = File(path).name + ".ann.json"
                val exportFile = File(dir, fileName)

                FileWriter(exportFile, false).use { fw ->
                    fw.write(json)
                    fw.flush()
                }

                Log.d(TAG, "Exported annotations to: ${exportFile.absolutePath}")
                sendExportResult(true, exportFile.absolutePath)
            } catch (e: IOException) {
                Log.e(TAG, "Export annotations failed", e)
                sendExportResult(false, e.message ?: "")
            }
        }
    }

    fun setPage(pageIndex: Int) {
        val view = pdfView
        if (isPdfViewReady() && view != null) {
            view.jumpTo(pageIndex, false)
        }
    }
}
