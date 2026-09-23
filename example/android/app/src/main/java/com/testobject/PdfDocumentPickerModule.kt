package com.testobject

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

object HostActivities {
    @Volatile
    var activity: Activity? = null
}

class PdfDocumentPickerModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext), ActivityEventListener {

    private var pending: Promise? = null

    init {
        reactContext.addActivityEventListener(this)
    }

    override fun getName(): String = NAME

    override fun invalidate() {
        reactContext.removeActivityEventListener(this)
        super.invalidate()
    }

    @ReactMethod
    fun pick(promise: Promise) {
        val activity = reactContext.currentActivity ?: HostActivities.activity
        if (activity == null) {
            promise.reject("NULL_PRESENTER", "Current activity is null")
            return
        }
        if (pending != null) {
            promise.reject("IN_PROGRESS", "Picker already open")
            return
        }
        pending = promise
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
        }
        try {
            activity.startActivityForResult(intent, REQUEST_CODE)
        } catch (e: Exception) {
            pending = null
            promise.reject("PICK_FAILED", e.message, e)
        }
    }

    override fun onActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (requestCode != REQUEST_CODE) return
        val promise = pending ?: return
        pending = null
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) {
            promise.reject("OPERATION_CANCELED", "User canceled document picker")
            return
        }
        thread(name = "pdf-document-copy") {
            try {
                val name = displayName(uri)
                val dir = File(reactContext.cacheDir, UUID.randomUUID().toString())
                if (!dir.mkdirs() && !dir.isDirectory) {
                    promise.reject("COPY_FAILED", "Cannot create cache directory")
                    return@thread
                }
                val dest = File(dir, name)
                reactContext.contentResolver.openInputStream(uri).use { input ->
                    if (input == null) {
                        promise.reject("COPY_FAILED", "Cannot open selected PDF")
                        return@thread
                    }
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                promise.resolve(
                    Arguments.createMap().apply {
                        putString("uri", uri.toString())
                        putString("name", name)
                        putString("localUri", Uri.fromFile(dest).toString())
                    },
                )
            } catch (e: Exception) {
                promise.reject("COPY_FAILED", e.message, e)
            }
        }
    }

    override fun onNewIntent(intent: Intent) = Unit

    private fun displayName(uri: Uri): String {
        val raw = reactContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val base = raw
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.ifBlank { null }
            ?: "document.pdf"
        return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
    }

    companion object {
        const val NAME = "PdfDocumentPicker"
        private const val REQUEST_CODE = 41042
    }
}
