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
        MapBuilder.builder<String, Int>()
            .put("undo", COMMAND_UNDO)
            .put("redo", COMMAND_REDO)
            .put("clear", COMMAND_CLEAR)
            .put("export", COMMAND_EXPORT)
            .put("exportPdf", COMMAND_EXPORT_PDF)
            .put("setPage", COMMAND_SET_PAGE)
            .build()

    override fun receiveCommand(view: PdfAnnotationView, commandId: String, args: ReadableArray?) {
        when (commandId) {
            "undo", "1" -> view.undo()
            "redo", "2" -> view.redo()
            "clear", "3" -> view.clearAll()
            "export", "4" -> {
                if (args != null && args.size() > 0) {
                    view.exportAnnotations(args.getString(0))
                }
            }
            "exportPdf", "5" -> {
                if (args != null && args.size() > 0) {
                    view.exportPdf(args.getString(0))
                }
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
    fun setAnnotationMode(view: PdfAnnotationView, annotationMode: Boolean?) {
        view.setAnnotationMode(annotationMode ?: false)
    }

    @ReactProp(name = "strokeColor")
    fun setStrokeColor(view: PdfAnnotationView, color: String?) {
        color?.let { view.setStrokeColor(it) }
    }

    @ReactProp(name = "strokeWidth", defaultFloat = 5f)
    fun setStrokeWidth(view: PdfAnnotationView, width: Float?) {
        view.setStrokeWidth(width ?: 5f)
    }

    @ReactProp(name = "minScale", defaultFloat = 1.0f)
    fun setMinScale(view: PdfAnnotationView, scale: Float?) {
        view.setMinScale(scale ?: 1.0f)
    }

    @ReactProp(name = "maxScale", defaultFloat = 5.0f)
    fun setMaxScale(view: PdfAnnotationView, scale: Float?) {
        view.setMaxScale(scale ?: 5.0f)
    }

    @ReactProp(name = "scale", defaultFloat = 1.0f)
    fun setScale(view: PdfAnnotationView, scale: Float?) {
        view.setInitialScale(scale ?: 1.0f)
    }

    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any>? =
        MapBuilder.builder<String, Any>()
            .put("onExportResult", MapBuilder.of("registrationName", "onExportResult"))
            .put("onExportPdfResult", MapBuilder.of("registrationName", "onExportPdfResult"))
            .put("onLoadComplete", MapBuilder.of("registrationName", "onLoadComplete"))
            .put("onTableOfContents", MapBuilder.of("registrationName", "onTableOfContents"))
            .put("onPageChanged", MapBuilder.of("registrationName", "onPageChanged"))
            .put("onError", MapBuilder.of("registrationName", "onError"))
            .put("onAnnotationChanged", MapBuilder.of("registrationName", "onAnnotationChanged"))
            .build()

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
