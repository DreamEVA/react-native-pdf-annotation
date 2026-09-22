package com.reactnativepdfannotation

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

class PdfAnnotationViewManager : SimpleViewManager<PdfAnnotationView>() {

    override fun getName(): String = REACT_CLASS

    override fun createViewInstance(reactContext: ThemedReactContext): PdfAnnotationView =
        PdfAnnotationView(reactContext)

    override fun getCommandsMap(): MutableMap<String, Int>? =
        mutableMapOf(
            "undo" to COMMAND_UNDO,
            "redo" to COMMAND_REDO,
            "clear" to COMMAND_CLEAR,
            "export" to COMMAND_EXPORT,
            "exportPdf" to COMMAND_EXPORT_PDF,
            "setPage" to COMMAND_SET_PAGE
        )

    override fun receiveCommand(view: PdfAnnotationView, commandId: String, args: ReadableArray?) {
        when (commandId) {
            "undo", "1" -> view.undo()
            "redo", "2" -> view.redo()
            "clear", "3" -> view.clearAll()
            "export", "4" -> {
                args?.getString(0)?.let { view.exportAnnotations(it) }
            }
            "exportPdf", "5" -> {
                args?.getString(0)?.let { view.exportPdf(it) }
            }
            "setPage", "6" -> {
                if (args != null && args.size() > 0) {
                    view.setPage(args.getInt(0))
                }
            }
            else -> super.receiveCommand(view, commandId, args)
        }
    }

    @ReactProp(name = "originalPath")
    fun setOriginalPath(view: PdfAnnotationView, originalPath: String?) {
        view.setOriginalPath(originalPath)
    }

    @ReactProp(name = "filePath")
    fun setFilePath(view: PdfAnnotationView, filePath: String?) {
        filePath?.let { view.loadPdf(it) }
    }

    @ReactProp(name = "annotationMode", defaultBoolean = false)
    fun setAnnotationMode(view: PdfAnnotationView, annotationMode: Boolean) {
        view.setAnnotationMode(annotationMode)
    }

    @ReactProp(name = "strokeColor")
    fun setStrokeColor(view: PdfAnnotationView, color: String?) {
        color?.let { view.setStrokeColor(it) }
    }

    @ReactProp(name = "strokeWidth", defaultFloat = 5f)
    fun setStrokeWidth(view: PdfAnnotationView, width: Float) {
        view.setStrokeWidth(width)
    }

    @ReactProp(name = "minScale", defaultFloat = 1.0f)
    fun setMinScale(view: PdfAnnotationView, scale: Float) {
        view.setMinScale(scale)
    }

    @ReactProp(name = "maxScale", defaultFloat = 5.0f)
    fun setMaxScale(view: PdfAnnotationView, scale: Float) {
        view.setMaxScale(scale)
    }

    @ReactProp(name = "scale", defaultFloat = 1.0f)
    fun setScale(view: PdfAnnotationView, scale: Float) {
        view.setInitialScale(scale)
    }

    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any>? =
        mutableMapOf(
            "onExportResult" to MapBuilder.of("registrationName", "onExportResult"),
            "onExportPdfResult" to MapBuilder.of("registrationName", "onExportPdfResult"),
            "onLoadComplete" to MapBuilder.of("registrationName", "onLoadComplete"),
            "onTableOfContents" to MapBuilder.of("registrationName", "onTableOfContents"),
            "onPageChanged" to MapBuilder.of("registrationName", "onPageChanged"),
            "onError" to MapBuilder.of("registrationName", "onError"),
            "onAnnotationChanged" to MapBuilder.of("registrationName", "onAnnotationChanged")
        )

    companion object {
        const val REACT_CLASS = "PdfAnnotationView"

        private const val COMMAND_UNDO = 1
        private const val COMMAND_REDO = 2
        private const val COMMAND_CLEAR = 3
        private const val COMMAND_EXPORT = 4
        private const val COMMAND_EXPORT_PDF = 5
        private const val COMMAND_SET_PAGE = 6
    }
}
