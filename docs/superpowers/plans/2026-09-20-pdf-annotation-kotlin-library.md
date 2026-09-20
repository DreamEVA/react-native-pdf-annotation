# react-native-pdf-annotation Kotlin 库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 meeting_pad 的 Java PDF 手写批注组件移植为 Kotlin 版 npm 包 `react-native-pdf-annotation`（RN 0.72+，双架构兼容），JS 使用方式不变。

**Architecture:** 标准 RN 库结构：`android/` Kotlin 库模块（SimpleViewManager + ReactPackage，autolinking 自动注册，新架构走 interop）+ `src/` TS 封装。事件发送用 RCTModernEventEmitter 优先（双架构兼容）。核心逻辑 1:1 移植原 Java（坐标归一化、undo/redo、防抖持久化、iText7 导出），删除前台服务/通知权限/AppLogger/AppPermissionModule 等宿主耦合。

**Tech Stack:** Kotlin 1.8.22、AndroidPdfViewer 3.2.0（pdfium）、iText7 7.2.5、Gson 2.10.1、TypeScript 5、react-native 0.73（devDep 仅供类型）

**参考源码（只读，不可修改）：**
- `path/to/original-app\android\app\src\main\java\com\example\app\pdf\PdfAnnotationView.java`（1615 行）
- `...\pdf\PdfAnnotationViewManager.java`、`...\pdf\PdfAnnotationPackage.java`、`...\pdf\AnnotationPersistence.java`
- `path/to/original-app\app\components\PdfAnnotationView\index.js`、`app\components\PdfViewer\index.js`

**验证环境说明：** 本机仅有 JDK 8、无 gradle、无 Android 工程，因此 Kotlin 无法本地编译；Kotlin 正确性通过逐方法对照移植 + 宿主工程编译（用户执行，见 Task 16）保证。TS 层可本地验证（node 20 / npm 10 已确认可用）。

---

### Task 1: 项目脚手架（package.json / tsconfig / .gitignore）

**Files:**
- Create: `package.json`
- Create: `tsconfig.json`
- Create: `tsconfig.build.json`
- Create: `.gitignore`

- [ ] **Step 1: 写 package.json**

```json
{
  "name": "react-native-pdf-annotation",
  "version": "1.0.0",
  "description": "PDF handwriting annotation native view for React Native (Android, Kotlin). Port of the meeting_pad Java component.",
  "main": "lib/index.js",
  "types": "lib/index.d.ts",
  "license": "MIT",
  "scripts": {
    "build": "tsc -p tsconfig.build.json",
    "typecheck": "tsc --noEmit",
    "prepare": "npm run build"
  },
  "peerDependencies": {
    "react": ">=17.0.0",
    "react-native": ">=0.72.0"
  },
  "devDependencies": {
    "@types/react": "^18.2.65",
    "react": "18.2.0",
    "react-native": "0.73.6",
    "typescript": "^5.4.5"
  },
  "files": [
    "lib",
    "src",
    "android",
    "!android/build",
    "!android/.gradle"
  ]
}
```

- [ ] **Step 2: 写 tsconfig.json（typecheck 用）**

```json
{
  "compilerOptions": {
    "target": "es2019",
    "module": "commonjs",
    "moduleResolution": "node",
    "lib": ["es2019"],
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "noEmit": true
  },
  "include": ["src"]
}
```

- [ ] **Step 3: 写 tsconfig.build.json（产出 lib/）**

```json
{
  "extends": "./tsconfig.json",
  "compilerOptions": {
    "noEmit": false,
    "declaration": true,
    "outDir": "lib",
    "rootDir": "src"
  }
}
```

- [ ] **Step 4: 写 .gitignore**

```
node_modules/
*.tgz
npm-debug.log*
```

- [ ] **Step 5: Commit**

```powershell
git add package.json tsconfig.json tsconfig.build.json .gitignore
git commit -m "chore: scaffold react-native-pdf-annotation npm package"
```

---

### Task 2: Android 工程文件（build.gradle / AndroidManifest）

**Files:**
- Create: `android/build.gradle`
- Create: `android/src/main/AndroidManifest.xml`

- [ ] **Step 1: 写 android/build.gradle**

```gradle
buildscript {
    ext.safeExtGet = { prop, fallback ->
        rootProject.ext.has(prop) ? rootProject.ext.get(prop) : fallback
    }

    repositories {
        google()
        mavenCentral()
        maven { url 'https://www.jitpack.io' }
    }

    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${safeExtGet('kotlinVersion', '1.8.22')}"
    }
}

apply plugin: 'com.android.library'
apply plugin: 'kotlin-android'

android {
    namespace 'com.reactnativepdfannotation'
    compileSdkVersion safeExtGet('compileSdkVersion', 34)

    defaultConfig {
        minSdkVersion safeExtGet('minSdkVersion', 23)
        targetSdkVersion safeExtGet('targetSdkVersion', 34)
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = '11'
    }
}

repositories {
    google()
    mavenCentral()
    maven { url 'https://www.jitpack.io' }
    def reactNativeAndroidDir = "$rootDir/../node_modules/react-native/android"
    if (file(reactNativeAndroidDir).exists()) {
        maven { url reactNativeAndroidDir }
    }
}

dependencies {
    implementation 'com.facebook.react:react-android:+'
    implementation 'com.github.barteksc:android-pdf-viewer:3.2.0'
    implementation 'com.itextpdf:itext7-core:7.2.5'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

- [ ] **Step 2: 写 android/src/main/AndroidManifest.xml**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 3: Commit**

```powershell
git add android/build.gradle android/src/main/AndroidManifest.xml
git commit -m "chore: add android library gradle config"
```

---

### Task 3: AnnotationPersistence.kt（Gson DTO，v3 格式）

**Files:**
- Create: `android/src/main/java/com/reactnativepdfannotation/AnnotationPersistence.kt`

**对照源:** `AnnotationPersistence.java`（191 行）。行为必须一致：version=3（width=屏幕像素），v2 反归一化兼容，损坏 JSON 静默失败。

- [ ] **Step 1: 写完整文件**

```kotlin
package com.reactnativepdfannotation

import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName

internal object AnnotationPersistence {

    private const val TAG = "AnnotationPersistence"
    const val CURRENT_VERSION = 3

    internal class PointDto(
        @SerializedName("x") var x: Float,
        @SerializedName("y") var y: Float
    ) {
        fun toPointF(): PointF = PointF(x, y)
    }

    internal class StrokeDto {
        @SerializedName("color") var color: String? = null
        @SerializedName("width") var width: Float = 0f
        @SerializedName("timestamp") var timestamp: Long = 0L
        @SerializedName("points") var points: MutableList<PointDto> = ArrayList()

        constructor()

        constructor(s: PdfAnnotationView.AnnotationStroke, viewWidthPx: Float, normalize: Boolean) {
            this.color = String.format("#%08X", s.color)
            this.width = if (normalize && viewWidthPx > 0) s.width / viewWidthPx else s.width
            this.timestamp = s.timestamp
            for (p in s.normalizedPoints) {
                points.add(PointDto(p.x, p.y))
            }
        }

        fun toStroke(pageIndex: Int, viewWidthPx: Float): PdfAnnotationView.AnnotationStroke {
            val c = try {
                Color.parseColor(color)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid color: $color, fallback RED")
                Color.RED
            }
            val px = if (viewWidthPx > 0) width * viewWidthPx else width
            val stroke = PdfAnnotationView.AnnotationStroke(pageIndex, c, px)
            stroke.timestamp = timestamp
            for (pd in points) {
                stroke.normalizedPoints.add(pd.toPointF())
            }
            return stroke
        }
    }

    internal class PageDto {
        @SerializedName("pageIndex") var pageIndex: Int = 0
        @SerializedName("strokes") var strokes: MutableList<StrokeDto> = ArrayList()

        constructor()

        constructor(pageIndex: Int) {
            this.pageIndex = pageIndex
        }
    }

    internal class AnnotationFile {
        @SerializedName("version") var version: Int = CURRENT_VERSION
        @SerializedName("pdfPath") var pdfPath: String? = null
        @SerializedName("savedAt") var savedAt: Long = 0L
        @SerializedName("pages") var pages: MutableList<PageDto> = ArrayList()

        constructor()

        constructor(pdfPath: String) {
            this.pdfPath = pdfPath
            this.savedAt = System.currentTimeMillis()
        }
    }

    private val GSON: Gson = GsonBuilder()
        .serializeSpecialFloatingPointValues()
        .create()

    fun toJson(
        pdfPath: String,
        pageAnnotations: Map<Int, MutableList<PdfAnnotationView.AnnotationStroke>>,
        viewWidthPx: Float
    ): String = serialize(pdfPath, pageAnnotations, viewWidthPx, false)

    fun toJsonNormalized(
        pdfPath: String,
        pageAnnotations: Map<Int, MutableList<PdfAnnotationView.AnnotationStroke>>,
        viewWidthPx: Float
    ): String = serialize(pdfPath, pageAnnotations, viewWidthPx, true)

    private fun serialize(
        pdfPath: String,
        pageAnnotations: Map<Int, MutableList<PdfAnnotationView.AnnotationStroke>>,
        viewWidthPx: Float,
        normalize: Boolean
    ): String {
        val file = AnnotationFile(pdfPath)

        for ((pageIndex, strokes) in pageAnnotations) {
            if (strokes.isEmpty()) continue
            val pageDto = PageDto(pageIndex)
            for (s in strokes) {
                pageDto.strokes.add(StrokeDto(s, viewWidthPx, normalize))
            }
            file.pages.add(pageDto)
        }

        return GSON.toJson(file)
    }

    fun fromJson(
        json: String?,
        viewWidthPx: Float
    ): HashMap<Int, MutableList<PdfAnnotationView.AnnotationStroke>> {
        val result = HashMap<Int, MutableList<PdfAnnotationView.AnnotationStroke>>()
        if (json.isNullOrEmpty()) return result

        try {
            val file = GSON.fromJson(json, AnnotationFile::class.java)
            if (file == null || file.pages == null) return result

            if (file.version > CURRENT_VERSION) {
                Log.w(TAG, "File version ${file.version} > current $CURRENT_VERSION")
            }

            val resolveWidth = if (file.version == 2) viewWidthPx else 0f

            for (page in file.pages) {
                if (page.strokes == null || page.strokes.isEmpty()) continue
                val strokes = ArrayList<PdfAnnotationView.AnnotationStroke>()
                for (dto in page.strokes) {
                    if (dto.points == null || dto.points.size < 2) continue
                    strokes.add(dto.toStroke(page.pageIndex, resolveWidth))
                }
                if (strokes.isNotEmpty()) result[page.pageIndex] = strokes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse annotation JSON", e)
        }

        return result
    }
}
```

- [ ] **Step 2: 自检（对照 Java 原版逐行确认）**

对照 `AnnotationPersistence.java` 检查：CURRENT_VERSION=3 ✓；toJson 走 normalize=false、toJsonNormalized 走 normalize=true ✓；fromJson 中 version==2 才反归一化 ✓；points.size<2 跳过 ✓。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/AnnotationPersistence.kt
git commit -m "feat: port AnnotationPersistence to Kotlin (gson dto v3)"
```

---

### Task 4: PdfAnnotationViewManager.kt

**Files:**
- Create: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationViewManager.kt`

**对照源:** `PdfAnnotationViewManager.java`（159 行）。REACT_CLASS、props、命令、事件名必须完全一致。

- [ ] **Step 1: 写完整文件**

```kotlin
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
    fun setFilePath(view: PdfAnnotationView, filePath: String) {
        view.loadPdf(filePath)
    }

    @ReactProp(name = "annotationMode", defaultBoolean = false)
    fun setAnnotationMode(view: PdfAnnotationView, annotationMode: Boolean) {
        view.setAnnotationMode(annotationMode)
    }

    @ReactProp(name = "strokeColor")
    fun setStrokeColor(view: PdfAnnotationView, color: String) {
        view.setStrokeColor(color)
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
```

- [ ] **Step 2: 自检**

对照 Java 版：8 个 props 名/默认值一致 ✓；6 个命令 ID 与字符串映射一致（receiveCommand 同时接受 "1"~"6"）✓；7 个事件 registrationName 一致 ✓；不再注册 AppPermissionModule（Package 层处理）✓。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationViewManager.kt
git commit -m "feat: port PdfAnnotationViewManager to Kotlin"
```

---

### Task 5: PdfAnnotationPackage.kt + PdfAnnotationLifecycle.kt

**Files:**
- Create: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationPackage.kt`
- Create: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationLifecycle.kt`

**对照源:** `PdfAnnotationPackage.java`（去掉 AppPermissionModule）；`MainActivity.java` 的 dispatchToPdfViews（99-144 行，抽成库工具）。

- [ ] **Step 1: 写 PdfAnnotationPackage.kt**

```kotlin
package com.reactnativepdfannotation

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class PdfAnnotationPackage : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> =
        emptyList()

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
        listOf(PdfAnnotationViewManager())
}
```

- [ ] **Step 2: 写 PdfAnnotationLifecycle.kt**

```kotlin
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
```

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationPackage.kt android/src/main/java/com/reactnativepdfannotation/PdfAnnotationLifecycle.kt
git commit -m "feat: port package registration and lifecycle dispatch to Kotlin"
```

---

### Task 6: PdfAnnotationView.kt — 类骨架与视图管理

**Files:**
- Create: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt`

**对照源:** `PdfAnnotationView.java` 行 61-167（字段/构造/init/createPdfView/recreatePdfView）、170-190（尺寸工具）、192-261（runWhenPdfViewMeasured）、263-283（releasePdfView）、285-302（onDetachedFromWindow）。

**移植变更点：** 删除 `shouldRunForegroundService` 字段、`ensureForegroundServiceIfNeeded` 方法、onDetachedFromWindow 中的 `PdfForegroundService.stop`；AppLogger → Log。

- [ ] **Step 1: 写文件头部 + 字段 + 构造 + 视图管理方法（此任务代码全部写入该文件）**

```kotlin
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
```

- [ ] **Step 2: 自检**

对照 Java 行 61-167/170-302：字段一一对应（去掉 shouldRunForegroundService）✓；runWhenPdfViewMeasured 的 executeOnce/listener/retry 逻辑与 Java 一致（20 次重试、50ms 间隔）✓；onDetachedFromWindow 不再调用前台服务 ✓。

- [ ] **Step 3: Commit（先提交骨架，后续任务继续扩充本文件）**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt
git commit -m "feat: port PdfAnnotationView skeleton and view lifecycle to Kotlin"
```

---

### Task 7: PdfAnnotationView.kt — 宿主生命周期 / 内存压力 / 属性设置 / 缩放

**Files:**
- Modify: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt`（在 onDetachedFromWindow 方法后、类末尾大括号前追加）

**对照源:** `PdfAnnotationView.java` 行 304-346（onHostPause/Stop/Resume）、445-486（onTrimMemory/recycle/setAnnotationMode）、490-522（stroke 设置/originalPath）、578-594（applyStableZoom）。

- [ ] **Step 1: 追加以下代码（插入到类的最后一个 `}` 之前）

```kotlin
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
```

- [ ] **Step 2: 自检**

对照 Java：onHostResume 去掉 ensureForegroundServiceIfNeeded ✓；setInitialScale 的立即应用分支保留 ✓；applyStableZoom 逻辑一致 ✓。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt
git commit -m "feat: port host lifecycle, memory pressure and zoom handling to Kotlin"
```

---

### Task 8: PdfAnnotationView.kt — PDF 加载与回调

**Files:**
- Modify: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt`（在 applyStableZoom 后追加）

**对照源:** `PdfAnnotationView.java` 行 340-415（startPdfLoadWhenMeasured）、417-443（reloadPdf）、524-576（loadPdf）。

**移植变更点：** loadPdf 删除通知权限检查与前台服务启动；其余完全一致。

- [ ] **Step 1: 追加以下代码**

```kotlin
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
```

- [ ] **Step 2: 自检**

对照 Java：所有 Configurator 链参数一致（enableSwipe/swipeHorizontal(false)/enableDoubletap(false)/enableAnnotationRendering/FitPolicy.WIDTH/fitEachPage/spacing(0)/autoSpacing(false)）✓；onRender 中 setMin/Mid/MaxZoom + applyStableZoom ✓；isReload 时跳过 loadAnnotationsFromDisk 和 sendLoadComplete ✓；loadPdf 不再调权限/前台服务 ✓。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt
git commit -m "feat: port pdf loading and render callbacks to Kotlin"
```

---

### Task 9: PdfAnnotationView.kt — 坐标变换

**Files:**
- Modify: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt`（在 loadPdf 后追加）

**对照源:** `PdfAnnotationView.java` 行 596-695。

- [ ] **Step 1: 追加以下代码**

```kotlin
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
```

- [ ] **Step 2: 自检**

对照 Java：docX/docY 公式、页面定位循环、±0.05 容差、coerceIn(0,1) 钳制、outPageIndex 回写 ✓。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt
git commit -m "feat: port normalized coordinate transforms to Kotlin"
```

---

### Task 10: PdfAnnotationView.kt — 撤销重做 / 清空 / 导出 JSON / 跳页

**Files:**
- Modify: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt`（在 normalizedToScreen 后追加）

**对照源:** `PdfAnnotationView.java` 行 697-833。

- [ ] **Step 1: 追加以下代码**

```kotlin
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
```

- [ ] **Step 2: 自检**

对照 Java：undo 的引用相等（`===`）移除 + 空 List 删 key ✓；redo 的 getOrPut 语义与 Java 一致 ✓；clearAll 先删磁盘文件 ✓；exportAnnotations 文件名 `{pdfName}.ann.json`、事件参数一致 ✓；setPage 0 基索引 jumpTo ✓。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt
git commit -m "feat: port undo/redo/export annotations to Kotlin"
```

---

### Task 11: PdfAnnotationView.kt — exportPdf（iText7 烘焙）

**Files:**
- Modify: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt`（在 setPage 后追加）

**对照源:** `PdfAnnotationView.java` 行 835-1022。

- [ ] **Step 1: 追加以下代码**

```kotlin
    fun exportPdf(exportPath: String) {
        val path = currentPdfPath
        if (path == null) {
            Log.w(TAG, "exportPdf: no PDF loaded")
            sendExportPdfResult(false, "No PDF loaded", null)
            return
        }

        if (pageAnnotations.isEmpty()) {
            IO_EXECUTOR.submit {
                try {
                    val src = File(path)
                    val dst = File(exportPath)
                    dst.parentFile?.let { if (!it.exists()) it.mkdirs() }
                    copyFile(src, dst)
                    sendExportPdfResult(true, "No annotations, original PDF copied", dst.absolutePath)
                } catch (e: IOException) {
                    Log.e(TAG, "exportPdf copy failed", e)
                    sendExportPdfResult(false, e.message ?: "", null)
                }
            }
            return
        }

        val snapshot = snapshotAnnotations()
        val srcPath = path
        val viewWidthPxSnapshot = getPdfViewWidthOrDefault()

        IO_EXECUTOR.submit {
            var pdfDoc: PdfDocument? = null
            try {
                val dstFile = File(exportPath)
                dstFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

                val tmpFile = File(dstFile.parent, dstFile.name + ".tmp")

                val readerProps = ReaderProperties()
                val reader = PdfReader(srcPath, readerProps)
                reader.setUnethicalReading(true)
                val writer = PdfWriter(tmpFile.absolutePath)
                pdfDoc = PdfDocument(reader, writer)

                val pageCount = pdfDoc.numberOfPages

                for ((pageIndex, strokes) in snapshot) {
                    val iTextPageNum = pageIndex + 1

                    if (iTextPageNum < 1 || iTextPageNum > pageCount) {
                        Log.w(TAG, "exportPdf: pageIndex $pageIndex out of range, skipped")
                        continue
                    }
                    if (strokes.isEmpty()) continue

                    val page = pdfDoc.getPage(iTextPageNum)
                    val mediaBox: Rectangle = page.mediaBox
                    val pageWidthPt = mediaBox.width
                    val pageHeightPt = mediaBox.height

                    val pdfCanvas = PdfCanvas(page.newContentStreamAfter(), page.resources, pdfDoc)
                    val viewWidthPx = viewWidthPxSnapshot

                    for (stroke in strokes) {
                        val pts = stroke.normalizedPoints
                        if (pts.size < 2) continue

                        val argb = stroke.color
                        val alpha = ((argb shr 24) and 0xFF) / 255f
                        val r = ((argb shr 16) and 0xFF) / 255f
                        val g = ((argb shr 8) and 0xFF) / 255f
                        val b = (argb and 0xFF) / 255f

                        val strokeWidthPt = maxOf(0.5f, (stroke.width / viewWidthPx) * pageWidthPt)

                        pdfCanvas.saveState()

                        pdfCanvas.setStrokeColor(DeviceRgb(r, g, b))
                        if (alpha < 1f) {
                            val gs = PdfExtGState()
                            gs.strokeOpacity = alpha
                            pdfCanvas.setExtGState(gs)
                        }
                        pdfCanvas.setLineWidth(strokeWidthPt)
                        pdfCanvas.setLineCapStyle(PdfCanvasConstants.LineCapStyle.ROUND)
                        pdfCanvas.setLineJoinStyle(PdfCanvasConstants.LineJoinStyle.ROUND)

                        val first = pts[0]
                        val startX = first.x * pageWidthPt
                        val startY = pageHeightPt * (1f - first.y)
                        pdfCanvas.moveTo(startX, startY)

                        if (pts.size == 2) {
                            val p1 = pts[1]
                            pdfCanvas.lineTo(p1.x * pageWidthPt, pageHeightPt * (1f - p1.y))
                        } else {
                            for (i in 1 until pts.size - 1) {
                                val p0 = pts[i - 1]
                                val p1 = pts[i]
                                val p2 = pts[i + 1]

                                val cp1x = (p0.x + p1.x) / 2f * pageWidthPt
                                val cp1y = pageHeightPt * (1f - (p0.y + p1.y) / 2f)
                                val cp2x = (p1.x + p2.x) / 2f * pageWidthPt
                                val cp2y = pageHeightPt * (1f - (p1.y + p2.y) / 2f)

                                pdfCanvas.curveTo(cp1x, cp1y, cp2x, cp2y, cp2x, cp2y)
                            }
                            val last = pts[pts.size - 1]
                            pdfCanvas.lineTo(last.x * pageWidthPt, pageHeightPt * (1f - last.y))
                        }

                        pdfCanvas.stroke()
                        pdfCanvas.restoreState()
                    }

                    pdfCanvas.release()
                }

                pdfDoc.close()
                pdfDoc = null

                if (!tmpFile.renameTo(dstFile)) {
                    copyFile(tmpFile, dstFile)
                    tmpFile.delete()
                }

                Log.d(TAG, "exportPdf success -> ${dstFile.absolutePath}")
                sendExportPdfResult(true, "Export successful", dstFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "exportPdf failed", e)
                sendExportPdfResult(false, e.message ?: "", null)
            } finally {
                pdfDoc?.let { doc ->
                    try {
                        doc.close()
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
    }
```

- [ ] **Step 2: 自检**

对照 Java：ARGB 分解、线宽换算 `(stroke.width / viewWidthPx) * pageWidthPt`、Y 翻转 `pageHeightPt * (1 - y)`、Catmull-Rom 简化贝塞尔控制点（cp1=(p0+p1)/2, cp2=(p1+p2)/2，终点=cp2）、2 点直线退化、tmp+rename 原子写、空批注直接复制 ✓。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt
git commit -m "feat: port itext7 pdf burn-in export to Kotlin"
```

---

### Task 12: PdfAnnotationView.kt — 事件发送（双架构）

**Files:**
- Modify: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt`（在 exportPdf 后追加）

**对照源:** `PdfAnnotationView.java` 行 1024-1188。

**移植变更点：** sendEventToJS 用 RCTModernEventEmitter 优先、RCTEventEmitter 兜底；删除 `hasActiveCatalystInstance()` 检查（bridgeless 兼容）。

- [ ] **Step 1: 追加以下代码**

```kotlin
    @Suppress("DEPRECATION")
    private fun sendEventToJS(eventName: String, event: WritableMap) {
        val ctx = reactContext ?: return
        val viewId = id

        Handler(Looper.getMainLooper()).post {
            if (viewId == View.NO_ID) {
                Log.w(TAG, "sendEventToJS: view not attached, dropping event: $eventName")
                return@post
            }
            try {
                val modernEmitter = ctx.getJSModule(RCTModernEventEmitter::class.java)
                if (modernEmitter != null) {
                    modernEmitter.receiveEvent(viewId, eventName, event)
                } else {
                    ctx.getJSModule(RCTEventEmitter::class.java).receiveEvent(viewId, eventName, event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send event: $eventName", e)
            }
        }
    }

    private fun sendExportPdfResult(success: Boolean, message: String, filePath: String?) {
        val event = Arguments.createMap()
        event.putBoolean("success", success)
        event.putString("message", message)
        filePath?.let { event.putString("filePath", it) }
        sendEventToJS("onExportPdfResult", event)
    }

    private fun sendLoadComplete(nbPages: Int, filePath: String) {
        val view = pdfView
        val pageSize: SizeF? = if (isPdfViewReady() && view != null) view.getPageSize(0) else null

        val event = Arguments.createMap()
        event.putInt("pageCount", nbPages)
        event.putString("filePath", filePath)
        event.putDouble("width", (pageSize?.width ?: 0f).toDouble())
        event.putDouble("height", (pageSize?.height ?: 0f).toDouble())

        sendEventToJS("onLoadComplete", event)
    }

    private fun sendTableOfContents(sourceView: PDFView, filePath: String) {
        val event = Arguments.createMap()
        event.putString("filePath", filePath)

        var tableOfContents: WritableArray = Arguments.createArray()
        try {
            if (!sourceView.isRecycled) {
                tableOfContents = createTableOfContentsArray(sourceView.tableOfContents)
            }
        } catch (e: Exception) {
            Log.w(TAG, "sendTableOfContents: failed to read table of contents: ${e.message}")
        }

        event.putArray("tableOfContents", tableOfContents)
        sendEventToJS("onTableOfContents", event)
    }

    private fun createTableOfContentsArray(bookmarks: List<Bookmark>?): WritableArray {
        val result = Arguments.createArray()
        if (bookmarks == null) return result

        for (bookmark in bookmarks) {
            val item = Arguments.createMap()
            item.putString("title", bookmark.title ?: "")
            item.putInt("page", bookmark.pageIdx.toInt())
            item.putInt("pageNumber", bookmark.pageIdx.toInt() + 1)
            item.putArray("children", createTableOfContentsArray(bookmark.children))
            result.pushMap(item)
        }

        return result
    }

    private fun sendPageChanged(page: Int, pageCount: Int) {
        val event = Arguments.createMap()
        event.putInt("page", page)
        event.putInt("pageCount", pageCount)

        sendEventToJS("onPageChanged", event)
    }

    private fun sendError(message: String) {
        val event = Arguments.createMap()
        event.putString("message", message)
        sendEventToJS("onError", event)
    }

    private fun sendAnnotationChanged() {
        val path = currentPdfPath ?: return

        val snapshot = snapshotAnnotations()
        val viewWidthPx = getPdfViewWidthOrDefault()

        IO_EXECUTOR.submit {
            val json = AnnotationPersistence.toJsonNormalized(path, snapshot, viewWidthPx)

            val event = Arguments.createMap()
            event.putString("data", json)

            sendEventToJS("onAnnotationChanged", event)
        }
    }

    private fun sendExportResult(success: Boolean, message: String) {
        val event = Arguments.createMap()
        event.putBoolean("success", success)
        event.putString("message", message)

        sendEventToJS("onExportResult", event)
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { fis ->
            FileOutputStream(dst).use { fos ->
                val buf = ByteArray(8192)
                var len = fis.read(buf)
                while (len != -1) {
                    fos.write(buf, 0, len)
                    len = fis.read(buf)
                }
                fos.flush()
            }
        }
    }
```

- [ ] **Step 2: 自检**

对照 Java：7 个事件名与字段完全一致 ✓；sendLoadComplete 的 width/height 取 pageSize(0) ✓；目录项递归结构（title/page/pageNumber/children）✓；onAnnotationChanged 在 IO 线程序列化、sendEventToJS 内部回主线程 ✓。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt
git commit -m "feat: port event emission with dual-arch emitter to Kotlin"
```

---

### Task 13: PdfAnnotationView.kt — 持久化 / 文件工具

**Files:**
- Modify: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt`（在 copyFile 后追加）

**对照源:** `PdfAnnotationView.java` 行 1190-1372。

- [ ] **Step 1: 追加以下代码**

```kotlin
    private fun scheduleAutoSave() {
        val path = currentPdfPath ?: return

        pendingSaveTask?.let { saveHandler.removeCallbacks(it) }

        val snapshot = snapshotAnnotations()
        val viewWidthPxSnapshot = getPdfViewWidthOrDefault()

        val task = Runnable {
            IO_EXECUTOR.submit { saveAnnotationsToDisk(path, snapshot, viewWidthPxSnapshot) }
        }
        pendingSaveTask = task
        saveHandler.postDelayed(task, SAVE_DEBOUNCE_MS)
    }

    private fun saveAnnotationsToDisk(pdfPath: String, annotations: Map<Int, MutableList<AnnotationStroke>>, viewWidthPx: Float) {
        try {
            val json = AnnotationPersistence.toJson(pdfPath, annotations, viewWidthPx)
            val file = getAnnotationFile(pdfPath)

            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Log.e(TAG, "Save annotations failed: cannot create dir ${parentDir.absolutePath}")
                    return
                }
            }

            val tmp = File(file.parent, file.name + ".tmp")
            FileWriter(tmp, false).use { fw ->
                fw.write(json)
                fw.flush()
            }

            if (!tmp.renameTo(file)) {
                FileWriter(file, false).use { fw ->
                    fw.write(json)
                    fw.flush()
                }
                tmp.delete()
            }

            Log.d(TAG, "Saved ${annotations.size} pages -> ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Save annotations failed: ${getAnnotationFile(pdfPath).absolutePath}", e)
        }
    }

    private fun loadAnnotationsFromDisk(pdfPath: String) {
        val file = getAnnotationFile(pdfPath)
        if (!file.exists()) {
            Log.d(TAG, "No annotation file for: $pdfPath")
            return
        }

        try {
            val json = readFileToString(file)
            val viewWidthPx = getPdfViewWidthOrDefault()
            val loaded = AnnotationPersistence.fromJson(json, viewWidthPx)
            pageAnnotations.putAll(loaded)

            val allStrokes = ArrayList<AnnotationStroke>()
            for (strokes in loaded.values) {
                allStrokes.addAll(strokes)
            }

            allStrokes.sortWith { a, b -> java.lang.Long.compare(a.timestamp, b.timestamp) }

            for (stroke in allStrokes) {
                undoStack.push(stroke)
                if (undoStack.size > MAX_HISTORY) {
                    undoStack.pollLast()
                }
            }

            Log.d(TAG, "Loaded ${loaded.size} pages, ${allStrokes.size} strokes from ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Load annotations failed, starting fresh", e)
            backupCorruptedFile(file)
        }
    }

    private fun deleteAnnotationFile() {
        val path = currentPdfPath ?: return

        pendingSaveTask?.let { saveHandler.removeCallbacks(it) }
        pendingSaveTask = null

        IO_EXECUTOR.submit {
            val file = getAnnotationFile(path)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Annotation file deleted: $deleted -> ${file.name}")
            }
        }
    }

    private fun snapshotAnnotations(): Map<Int, MutableList<AnnotationStroke>> {
        val copy = HashMap<Int, MutableList<AnnotationStroke>>()
        for ((page, strokes) in pageAnnotations) {
            copy[page] = ArrayList(strokes)
        }
        return copy
    }

    private fun getAnnotationFile(pdfPath: String): File {
        val basePath = originalPdfPath ?: pdfPath
        return File(basePath + ".ann.json")
    }

    private fun readFileToString(file: File): String {
        val sb = StringBuilder(file.length().toInt())
        BufferedReader(FileReader(file)).use { br ->
            var line = br.readLine()
            while (line != null) {
                sb.append(line).append('\n')
                line = br.readLine()
            }
        }
        return sb.toString()
    }

    private fun backupCorruptedFile(file: File) {
        val backup = File(file.parent, file.name + ".corrupt")
        backup.delete()
        file.renameTo(backup)
    }
```

- [ ] **Step 2: 自检**

对照 Java：SAVE_DEBOUNCE_MS=300 防抖 + 主线程快照 ✓；原子写（tmp+rename+降级覆盖）✓；加载按 timestamp 升序重建 undoStack（栈顶最新）✓；getAnnotationFile 优先 originalPdfPath ✓；损坏文件备份 .corrupt ✓。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt
git commit -m "feat: port annotation persistence with debounced atomic save to Kotlin"
```

---

### Task 14: PdfAnnotationView.kt — 内部类（AnnotationStroke / SafePdfView / AnnotationOverlay）

**Files:**
- Modify: `android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt`（在 backupCorruptedFile 后追加，作为类的最后成员）

**对照源:** `PdfAnnotationView.java` 行 1374-1388（AnnotationStroke）、1390-1487（SafePdfView）、1489-1614（AnnotationOverlay）。

- [ ] **Step 1: 追加以下代码（放在类内最后一个方法之后）**

```kotlin
    class AnnotationStroke(
        val pageIndex: Int,
        val color: Int,
        val width: Float
    ) {
        val normalizedPoints = ArrayList<PointF>()
        var timestamp: Long = System.currentTimeMillis()
    }

    private inner class SafePdfView(context: Context) : PDFView(context, null) {
        private var lastWidth = 0
        private var lastHeight = 0
        private var lastTapUpTime = 0L
        private var lastTapX = 0f
        private var lastTapY = 0f
        private var downX = 0f
        private var downY = 0f
        private val doubleTapTimeout: Int
        private val doubleTapSlop: Int
        private val touchSlop: Int

        init {
            val config = ViewConfiguration.get(context)
            doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout()
            doubleTapSlop = config.scaledDoubleTapSlop
            touchSlop = config.scaledTouchSlop
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            if ((w > 0 && h > 0) || lastWidth > 0 || lastHeight > 0) {
                super.onSizeChanged(w, h, lastWidth, lastHeight)
                lastWidth = w
                lastHeight = h
            }
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && event.pointerCount == 1) {
                downX = event.x
                downY = event.y
            }

            if (event.actionMasked == MotionEvent.ACTION_UP && event.pointerCount == 1) {
                val tapDx = event.x - downX
                val tapDy = event.y - downY
                val isTap = tapDx * tapDx + tapDy * tapDy <= touchSlop * touchSlop
                if (!isTap) {
                    lastTapUpTime = 0
                    return super.dispatchTouchEvent(event)
                }

                val now = event.eventTime
                val dx = event.x - lastTapX
                val dy = event.y - lastTapY
                val isDoubleTap = lastTapUpTime > 0 &&
                        now - lastTapUpTime <= doubleTapTimeout &&
                        dx * dx + dy * dy <= doubleTapSlop * doubleTapSlop

                if (isDoubleTap) {
                    lastTapUpTime = 0
                    cancelPdfViewGesture(event)
                    handleCustomDoubleTap(event.x, event.y)
                    return true
                }

                lastTapUpTime = now
                lastTapX = event.x
                lastTapY = event.y
            }

            if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.pointerCount > 1) {
                lastTapUpTime = 0
            }

            return super.dispatchTouchEvent(event)
        }

        private fun cancelPdfViewGesture(sourceEvent: MotionEvent) {
            val cancelEvent = MotionEvent.obtain(sourceEvent)
            cancelEvent.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancelEvent)
            cancelEvent.recycle()
        }

        private fun handleCustomDoubleTap(x: Float, y: Float) {
            if (!isPdfViewReady()) return

            val currentZoom = zoom
            val midZoom = (minScale + maxScale) / 2f
            val targetZoom = when {
                currentZoom < midZoom -> midZoom
                currentZoom < maxScale -> maxScale
                else -> initialScale
            }.coerceIn(minScale, maxScale)

            stopFling()
            zoomWithAnimation(x, y, targetZoom)
        }
    }

    private inner class AnnotationOverlay(context: Context) : View(context) {
        private val paint = Paint()

        init {
            paint.style = Paint.Style.STROKE
            paint.isAntiAlias = true
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            val view = pdfView
            if (isPaused || !isAttachedToWindow || view == null || view.isRecycled) {
                return
            }
            super.onDraw(canvas)
            if (isPaused || totalPages <= 0 || view.isRecycled) return

            for (strokes in pageAnnotations.values) {
                for (stroke in strokes) {
                    drawStroke(canvas, stroke)
                }
            }

            val current = currentStroke
            if (current != null && current.normalizedPoints.size > 1) {
                drawStroke(canvas, current)
            }
        }

        private fun drawStroke(canvas: Canvas, stroke: AnnotationStroke) {
            val view = pdfView ?: return
            if (!isPdfViewReady()) return
            if (stroke.normalizedPoints.size < 2) return

            paint.color = stroke.color
            paint.strokeWidth = stroke.width * view.zoom

            val path = Path()
            var started = false

            for (point in stroke.normalizedPoints) {
                val screenPoint = normalizedToScreen(stroke.pageIndex, point.x, point.y) ?: continue
                if (!started) {
                    path.moveTo(screenPoint.x, screenPoint.y)
                    started = true
                } else {
                    path.lineTo(screenPoint.x, screenPoint.y)
                }
            }

            if (started) {
                canvas.drawPath(path, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val view = pdfView
            if (!isPdfViewReady() || view == null) {
                currentStroke = null
                return false
            }
            if (!isAnnotationMode) {
                return false
            }

            if (event.pointerCount > 1) {
                if (currentStroke != null) {
                    currentStroke = null
                    invalidate()
                }
                return view.dispatchTouchEvent(event)
            }

            val x = event.x
            val y = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val pageIndex = intArrayOf(currentPage)
                    val normalized = screenToNormalized(x, y, pageIndex)
                    if (normalized != null) {
                        val stroke = AnnotationStroke(pageIndex[0], strokeColor, strokeWidth)
                        stroke.normalizedPoints.add(normalized)
                        currentStroke = stroke
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val current = currentStroke
                    if (current != null) {
                        val movePageIndex = intArrayOf(current.pageIndex)
                        val normalized = screenToNormalized(x, y, movePageIndex)
                        if (normalized != null && movePageIndex[0] == current.pageIndex) {
                            current.normalizedPoints.add(normalized)
                            invalidate()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val current = currentStroke
                    if (current != null && current.normalizedPoints.size > 1) {
                        commitStroke(current)
                    }
                    currentStroke = null
                    invalidate()
                }
            }

            return true
        }
    }
```

- [ ] **Step 2: 自检（整文件完整性检查）**

1. 用 read 工具通读 `PdfAnnotationView.kt` 全文，确认括号配对、类结构完整（外类 + 2 个 inner 类 + 1 个嵌套类）。
2. 对照 Java 原文件逐方法过一遍：SafePdfView 双击三档缩放（mid→max→initial）✓；跨页笔画丢弃（movePageIndex != current.pageIndex 不采样）✓；笔画宽度 `stroke.width * view.zoom` 随缩放 ✓；多指取消当前笔画并转发 PDFView ✓；非批注模式返回 false 穿透 ✓。
3. 用 Grep 工具在 `android/src/main/java/com/reactnativepdfannotation/` 目录搜索 pattern
   `AppLogger|PdfForegroundService|MainActivity|ensureNotificationPermission|ensureForegroundService`，
   必须 0 匹配（确认耦合已清除）。

- [ ] **Step 3: Commit**

```powershell
git add android/src/main/java/com/reactnativepdfannotation/PdfAnnotationView.kt
git commit -m "feat: port stroke model, SafePdfView and AnnotationOverlay to Kotlin"
```

---

### Task 15: TS 层（types.ts / PdfAnnotationView.ts / index.ts）+ 类型验证

**Files:**
- Create: `src/types.ts`
- Create: `src/PdfAnnotationView.ts`
- Create: `src/index.ts`

- [ ] **Step 1: 写 src/types.ts**

```ts
import type { NativeSyntheticEvent, ViewProps } from 'react-native';

export interface PdfAnnotationTableOfContentItem {
  title: string;
  page: number;
  pageNumber: number;
  children: PdfAnnotationTableOfContentItem[];
}

export interface PdfAnnotationLoadCompleteEvent {
  pageCount: number;
  filePath: string;
  width: number;
  height: number;
}

export interface PdfAnnotationPageChangedEvent {
  page: number;
  pageCount: number;
}

export interface PdfAnnotationTableOfContentsEvent {
  filePath: string;
  tableOfContents: PdfAnnotationTableOfContentItem[];
}

export interface PdfAnnotationErrorEvent {
  message: string;
}

export interface PdfAnnotationChangedEvent {
  data: string;
}

export interface PdfAnnotationExportResultEvent {
  success: boolean;
  message: string;
}

export interface PdfAnnotationExportPdfResultEvent {
  success: boolean;
  message: string;
  filePath?: string;
}

export type PdfAnnotationCommand =
  | 'undo'
  | 'redo'
  | 'clear'
  | 'export'
  | 'exportPdf'
  | 'setPage';

export interface PdfAnnotationViewProps extends ViewProps {
  filePath?: string;
  originalPath?: string;
  annotationMode?: boolean;
  strokeColor?: string;
  strokeWidth?: number;
  scale?: number;
  minScale?: number;
  maxScale?: number;
  onLoadComplete?: (event: NativeSyntheticEvent<PdfAnnotationLoadCompleteEvent>) => void;
  onPageChanged?: (event: NativeSyntheticEvent<PdfAnnotationPageChangedEvent>) => void;
  onTableOfContents?: (event: NativeSyntheticEvent<PdfAnnotationTableOfContentsEvent>) => void;
  onError?: (event: NativeSyntheticEvent<PdfAnnotationErrorEvent>) => void;
  onAnnotationChanged?: (event: NativeSyntheticEvent<PdfAnnotationChangedEvent>) => void;
  onExportResult?: (event: NativeSyntheticEvent<PdfAnnotationExportResultEvent>) => void;
  onExportPdfResult?: (event: NativeSyntheticEvent<PdfAnnotationExportPdfResultEvent>) => void;
}
```

- [ ] **Step 2: 写 src/PdfAnnotationView.ts**

```ts
import { requireNativeComponent } from 'react-native';
import type { PdfAnnotationViewProps } from './types';

const PdfAnnotationView = requireNativeComponent<PdfAnnotationViewProps>('PdfAnnotationView');

export default PdfAnnotationView;
```

- [ ] **Step 3: 写 src/index.ts**

```ts
export { default } from './PdfAnnotationView';
export type {
  PdfAnnotationViewProps,
  PdfAnnotationCommand,
  PdfAnnotationTableOfContentItem,
  PdfAnnotationLoadCompleteEvent,
  PdfAnnotationPageChangedEvent,
  PdfAnnotationTableOfContentsEvent,
  PdfAnnotationErrorEvent,
  PdfAnnotationChangedEvent,
  PdfAnnotationExportResultEvent,
  PdfAnnotationExportPdfResultEvent,
} from './types';
```

- [ ] **Step 4: 安装依赖并验证类型**

```powershell
npm install
npm run typecheck
```

Expected: `npm run typecheck` 无错误输出（exit code 0）。若 npm 网络受限导致 install 失败，跳过本步并在最终验证时重试。

- [ ] **Step 5: 构建 lib/**

```powershell
npm run build
```

Expected: 生成 `lib/index.js`、`lib/index.d.ts`、`lib/PdfAnnotationView.js`、`lib/PdfAnnotationView.d.ts`、`lib/types.js`、`lib/types.d.ts`。

- [ ] **Step 6: Commit**

```powershell
git add src lib package-lock.json
git commit -m "feat: add typescript wrapper for PdfAnnotationView"
```

---

### Task 16: README + 最终验证

**Files:**
- Create: `README.md`

- [ ] **Step 1: 写 README.md**

````markdown
# react-native-pdf-annotation

Kotlin 版 PDF 手写批注原生组件（Android）。基于 AndroidPdfViewer 3.2.0（pdfium）+ iText7，
支持 RN 0.72+（旧架构/新架构 interop 兼容）。

## 安装

```bash
npm install react-native-pdf-annotation
# 或本地路径
npm install ../ReactNativePDF_handwritten
```

autolinking 自动注册原生模块（ReactNative CLI ≥ 0.60）。

### 宿主工程必须的改动

1. 删除旧 Java 组件（如存在）：`android/app/src/main/java/com/example/originalapp/pdf/` 下的
   `PdfAnnotationView.java`、`PdfAnnotationViewManager.java`、`PdfAnnotationPackage.java`、
   `AnnotationPersistence.java`（否则 REACT_CLASS 冲突）。
2. `MainActivity` 接入生命周期分发（替代原先手写的 View 树遍历）：

```java
import com.reactnativepdfannotation.PdfAnnotationLifecycle;

@Override protected void onPause() {
    super.onPause();
    PdfAnnotationLifecycle.onHostPause(this);
}
@Override protected void onStop() {
    super.onStop();
    PdfAnnotationLifecycle.onHostStop(this);
}
@Override protected void onResume() {
    super.onResume();
    PdfAnnotationLifecycle.onHostResume(this);
}
@Override public void onTrimMemory(int level) {
    super.onTrimMemory(level);
    PdfAnnotationLifecycle.onTrimMemory(this, level);
}
```

3. gradle 可选覆盖（root build.gradle ext）：`kotlinVersion`（默认 1.8.22）、
   `compileSdkVersion`（默认 34）、`minSdkVersion`（默认 23）、`targetSdkVersion`（默认 34）。

## 使用

```tsx
import PdfAnnotationView from 'react-native-pdf-annotation';

<PdfAnnotationView
  ref={pdfRef}
  style={{ flex: 1 }}
  filePath="file:///path/to/copy.pdf"
  originalPath="file:///path/to/original.pdf"
  annotationMode={true}
  strokeColor="#FF0000"
  strokeWidth={5}
  scale={1}
  minScale={0.5}
  maxScale={5.0}
  onLoadComplete={({ nativeEvent }) => console.log(nativeEvent.pageCount)}
  onPageChanged={({ nativeEvent }) => console.log(nativeEvent.page)}
  onError={({ nativeEvent }) => console.log(nativeEvent.message)}
  onAnnotationChanged={({ nativeEvent }) => console.log(nativeEvent.data)}
/>
```

## Props

| prop | 类型 | 默认 | 说明 |
|---|---|---|---|
| filePath | string | - | PDF 文件路径（file:// 或绝对路径），触发加载 |
| originalPath | string | - | 公共目录原始路径，批注文件 `{originalPath}.ann.json` 存于其旁 |
| annotationMode | boolean | false | true 时单指绘制批注，多指仍可缩放/滚动 |
| strokeColor | string | - | 画笔颜色，如 `#FF0000` |
| strokeWidth | number | 5 | 画笔宽度（屏幕像素） |
| scale | number | 1 | 初始缩放 |
| minScale | number | 1 | 最小缩放 |
| maxScale | number | 5 | 最大缩放 |

## 命令（UIManager.dispatchViewManagerCommand）

| 命令 | 参数 | 说明 |
|---|---|---|
| undo | - | 撤销上一笔 |
| redo | - | 重做 |
| clear | - | 清空全部批注 |
| export | [exportDir] | 导出批注 JSON（`{pdfName}.ann.json`），回调 onExportResult |
| exportPdf | [exportPath] | 批注烘焙进 PDF，回调 onExportPdfResult |
| setPage | [pageIndex] | 跳转页（0 基索引） |

```ts
import { UIManager, findNodeHandle } from 'react-native';
UIManager.dispatchViewManagerCommand(findNodeHandle(pdfRef.current), 'exportPdf', [exportPath]);
```

## 事件

`onLoadComplete({pageCount, filePath, width, height})`、`onPageChanged({page, pageCount})`（page 为 0 基）、
`onTableOfContents({filePath, tableOfContents})`、`onError({message})`、
`onAnnotationChanged({data})`（归一化 JSON）、`onExportResult({success, message})`、
`onExportPdfResult({success, message, filePath?})`。

## 说明

- 批注数据自动防抖（300ms）保存为 `{originalPath}.ann.json`（version=3 格式），
  随 PDF 同目录存放，卸载 App 不丢失。
- 依赖 iText7（AGPL 许可），商用发布前请评估许可证合规。
- 组件在 Activity onPause/内存压力时会回收 pdfium 原生资源，必须在 MainActivity
  接入 PdfAnnotationLifecycle（见上文），否则可能出现原生崩溃。
````

- [ ] **Step 2: 最终验证（本地可执行部分）**

```powershell
npm run typecheck
npm pack --dry-run
```

Expected:
- typecheck exit code 0
- `npm pack --dry-run` 输出包含：`lib/index.js`、`lib/index.d.ts`、`src/*`、`android/build.gradle`、`android/src/main/AndroidManifest.xml`、`android/src/main/java/com/reactnativepdfannotation/*.kt`、`README.md`，且不包含 `node_modules`、`android/build`、`*.tgz`。

- [ ] **Step 3: 与 spec 逐项对照自查**

| spec 要求 | 落实位置 |
|---|---|
| Kotlin 移植、不改 Java | android/src/main/java/com/reactnativepdfannotation/*.kt（E 盘源码未动） |
| RN 0.72+ 双架构 | SimpleViewManager + interop；RCTModernEventEmitter 优先 |
| 去耦合（前台服务/通知权限/AppLogger/AppPermissionModule） | Task 14 Step 2 的 grep 检查 |
| JS 使用方式不变 | REACT_CLASS=``PdfAnnotationView``、props/命令/事件名与 Java 版逐一一致（Task 4 自检） |
| npm 包 | package.json main/types/files；npm pack --dry-run |
| 宿主集成说明 | README（删旧 Java、PdfAnnotationLifecycle、gradle 覆盖） |
| iText AGPL 声明 | README「说明」节 |

- [ ] **Step 4: 剩余验证说明（需用户执行）**

本机无 Android 编译环境（JDK 8、无 gradle），Kotlin 编译验证需在宿主工程执行：

1. 在宿主 `android/` 目录运行 `gradlew :app:assembleDebug`（集成本包后）。
2. 真机冒烟：加载 PDF、批注绘制、撤销/重做、持久化（杀进程重开）、导出 JSON/PDF、翻页、缩放、横竖屏切换。
3. 如编译报错，对照 `PdfAnnotationView.java` 原文与对应 `.kt` 方法修复（错误信息会指向具体行）。

- [ ] **Step 5: Commit**

```powershell
git add README.md
git commit -m "docs: add readme with integration guide"
```
