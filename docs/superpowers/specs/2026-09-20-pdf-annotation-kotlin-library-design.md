# 设计文档：react-native-pdf-annotation（Kotlin 版 PDF 手写批注 npm 包）

日期：2026-09-20
状态：已评审

## 1. 背景与目标

现有项目（meeting_pad，RN 0.66）中有一套 PDF 手写批注组件（Java 实现，位于
`android/app/src/main/java/com/example/originalapp/pdf/`），功能成熟稳定。

目标：

1. 将其移植为 Kotlin 版本，适配高版本 RN（0.72+，双架构兼容）。
2. 抽取为独立的 npm 包 `react-native-pdf-annotation`，放入当前目录
   `C:\path\to\library`。
3. 不允许修改原项目中的 Java 源码（E 盘为只读参考）。
4. JS 使用方式（组件名、props、命令、事件）完全不变。

## 2. 方案选择

- **方案 A（选定）**：标准 RN 库结构 —— `android/` Kotlin 库模块（旧架构
  SimpleViewManager）+ `src/` TS 封装；autolinking 自动注册；新架构（Fabric）
  通过 RN interop 层兼容。npm 包开箱即用，宿主零 gradle 改动。
- 方案 B（否决）：仅提供源码 + gradle 片段，不是真正的 npm 包。
- 方案 C（否决）：codegen 原生 Fabric 组件，工作量大，且需求只需双架构兼容。

## 3. 包结构

```
react-native-pdf-annotation/
├── package.json            # main/types 直接指向 src（不编译产物），peerDeps: react, react-native
├── tsconfig.json
├── android/
│   ├── build.gradle        # com.android.library + kotlin-android，safeExtGet 模式
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/reactnativepdfannotation/
│           ├── PdfAnnotationView.kt          # 核心视图（移植全部逻辑）
│           ├── PdfAnnotationViewManager.kt   # SimpleViewManager
│           ├── PdfAnnotationPackage.kt       # ReactPackage（autolink 自动发现）
│           ├── AnnotationPersistence.kt      # Gson DTO v3
│           └── PdfAnnotationLifecycle.kt     # 宿主 Activity 生命周期分发工具
├── src/
│   ├── index.ts             # default 导出 + 类型导出
│   ├── PdfAnnotationView.tsx # requireNativeComponent + TS 类型
│   └── types.ts
└── README.md
```

## 4. Kotlin 移植设计

### 4.1 文件职责

| 文件 | 职责 |
|---|---|
| `PdfAnnotationView.kt` | FrameLayout：`PDFView`（渲染）+ `AnnotationOverlay`（批注绘制）。含坐标归一化、undo/redo、防抖持久化、iText7 导出 PDF、生命周期暂停/recycle 防崩、SafePdfView 双击缩放 |
| `PdfAnnotationViewManager.kt` | `SimpleViewManager<PdfAnnotationView>`，REACT_CLASS=`"PdfAnnotationView"`；@ReactProp props；命令 map（undo/redo/clear/export/exportPdf/setPage）；事件 map |
| `PdfAnnotationPackage.kt` | `ReactPackage`，只返回 `PdfAnnotationViewManager`（不再注册 AppPermissionModule） |
| `AnnotationPersistence.kt` | Gson DTO，version=3（width=屏幕像素），v1/v2 兼容反序列化 |
| `PdfAnnotationLifecycle.kt` | 静态工具对象：遍历 Activity 的 View 树，对每个 PdfAnnotationView 分发 onHostPause/onHostStop/onHostResume/onTrimMemory |

### 4.2 去耦合清单

| 原 Java 依赖 | 处理 |
|---|---|
| `AppLogger` | 替换为 `android.util.Log`（TAG 不变） |
| `PdfForegroundService`（鸿蒙防后台清理） | 删除（`ensureForegroundServiceIfNeeded`、`shouldRunForegroundService` 相关代码全部移除） |
| `MainActivity.ensureNotificationPermissionBeforeOpenPdf` | 删除（loadPdf 中的权限检查移除） |
| `AppPermissionModule` | 删除（Package 中不再注册） |
| `onDetachedFromWindow` 中 `PdfForegroundService.stop` | 删除 |

### 4.3 双架构兼容关键点

- **事件发送**：优先 `reactContext.getJSModule(RCTModernEventEmitter::class.java)`，
  fallback 旧 `RCTEventEmitter`。新架构 bridgeless 下 `getJSModule(RCTEventEmitter)`
  不可用；RCTModernEventEmitter 在旧架构（0.66+）也存在，双架构通吃。
  沿用原版的安全检查：主线程 post、viewId 校验、try-catch；删除
  `hasActiveCatalystInstance()` 检查（新架构无 CatalystInstance 会误判，改为
  emitter 判空）。
- **命令**：保留 `receiveCommand(view, commandId: String, args)`（同时兼容字符串与
  数字字符串两种 commandId）。
- **Props/事件注册**：保留 @ReactProp 与
  `getExportedCustomDirectEventTypeConstants()`，interop 层自动处理新架构。

### 4.4 保留的核心逻辑（行为与原版完全一致）

- 归一化坐标（0~1 页面相对）：`screenToNormalized` / `normalizedToScreen`
- 批注层交互：单指绘制（annotationMode 开启）、多指转发 PDFView、跨页笔画丢弃
- SafePdfView：自定义双击缩放（mid→max→initial 三档循环）
- undo/redo 全局跨页栈（MAX_HISTORY=100，timestamp 恢复顺序）
- 300ms 防抖自动保存至 `{originalPath}.ann.json`（优先公共目录原始路径），
  单线程 IO Executor + 临时文件 rename 原子写；损坏文件备份为 `.corrupt`
- iText7 导出 PDF：归一化→pt 坐标、线宽按 `stroke.width/viewWidthPx*pageWidthPt`
  换算、Catmull-Rom→贝塞尔平滑、临时文件+rename
- `Constants.Cache.CACHE_SIZE = 120`（全局静态，行为与原版一致）
- onHostPause/onHostStop 时 recycle PDFView 防 SIGSEGV；onHostResume 重载并恢复
  页码/缩放；onTrimMemory(UI_HIDDEN) recycle
- PDF 配置：`fromFile`、swipeHorizontal(false)、enableSwipe、FitPolicy.WIDTH、
  fitEachPage、min/mid/max zoom、onLoad/onRender/onError/onPageChange/onPageScroll 回调

### 4.5 依赖与 gradle 配置

- `com.github.barteksc:android-pdf-viewer:3.2.0`（正式版，API 与 3.2.0-beta.1 一致）
- `com.itextpdf:itext7-core:7.2.5`（AGPL，README 声明商用注意）
- `com.google.code.gson:gson:2.10.1`
- `compileOnly com.facebook.react:react-native:+`（由宿主提供）
- safeExtGet 模式：compileSdk 34（默认）、minSdk 23（默认）、
  Kotlin 1.8.22（默认，宿主可通过 rootProject.ext 覆盖）
- `namespace "com.reactnativepdfannotation"`
- Kotlin 中访问 Java 静态字段 `Constants.Cache.CACHE_SIZE` 需注意 JvmStatic 兼容
  （android-pdf-viewer 为 Java 库，直接可访问）

## 5. JS/TS 层设计

### 5.1 API（与原版完全一致）

```tsx
<PdfAnnotationView
  filePath="file://..."        // 私有副本路径（触发 loadPdf）
  originalPath="file://..."    // 公共原始路径（决定批注文件存储位置）
  annotationMode={boolean}
  strokeColor="#RRGGBB"
  strokeWidth={number}
  scale={number}               // 初始缩放
  minScale={number}
  maxScale={number}
  onLoadComplete={({pageCount, filePath, width, height}) => ...}
  onPageChanged={({page, pageCount}) => ...}   // page 为 0 基
  onTableOfContents={({tableOfContents}) => ...}
  onError={({message}) => ...}
  onAnnotationChanged={({data}) => ...}        // data = 归一化 JSON 字符串
  onExportResult={({success, message}) => ...}
  onExportPdfResult={({success, message, filePath}) => ...}
/>
```

命令派发（宿主通过 UIManager，与原版一致）：

```ts
UIManager.dispatchViewManagerCommand(findNodeHandle(ref.current), 'undo', []);
UIManager.dispatchViewManagerCommand(handle, 'export', [exportPath]);
UIManager.dispatchViewManagerCommand(handle, 'exportPdf', [exportPath]);
UIManager.dispatchViewManagerCommand(handle, 'setPage', [pageIndex]); // 0 基
```

### 5.2 文件

- `src/types.ts`：props/事件类型
- `src/PdfAnnotationView.tsx`：`requireNativeComponent('PdfAnnotationView')` + 类型
- `src/index.ts`：默认导出组件，命名导出类型
- package.json：`main`/`types`/`react-native` 字段，peerDependencies:
  `react >=17`、`react-native >=0.72`

## 6. 宿主项目集成步骤

**适用范围（2026-09-20 决策）：本包面向 RN 0.72+ 的新工程（或已升级工程）使用，不修改
原 RN 0.66 Java 工程。** 原工程的 `PdfViewer/index.js` 业务封装可参考迁移（JS API 不变）。

1. `npm install <本包路径或 npm 包名>`（autolinking 自动注册 Kotlin 模块）
2. 如宿主工程曾内嵌同名旧组件（REACT_CLASS `PdfAnnotationView`），删除其旧 Java/Kotlin
   源码，避免冲突（全新工程无此步骤）
3. `MainActivity`（Java/Kotlin 均可）调用库的
   `PdfAnnotationLifecycle`（onPause/onStop/onResume/onTrimMemory 各一行）
4. JS 侧 `import PdfAnnotationView from 'react-native-pdf-annotation'`
5. 命令派发、事件、Pagination 等业务封装方式与原版完全一致

## 7. 风险与缓解

| 风险 | 缓解 |
|---|---|
| android-pdf-viewer 3.2.0 API 差异 | 构建期验证；如有差异按 3.2.0 API 调整（不改变对外行为） |
| 新架构 bridgeless 事件不通 | RCTModernEventEmitter 优先（业界标准做法） |
| iText7 AGPL 许可 | README 声明；商用需评估 |
| 原 `Constants.Cache` 全局静态影响宿主其他 PDF 组件 | 行为与原版一致，不改 |
| 无真实设备验证 | 编译通过 + 原项目集成冒烟（由用户执行）；本目录不包含可运行宿主工程 |

## 8. 验证方式

- Kotlin 模块可被独立编译（`gradle :android:assemble` 或宿主工程集成编译）
- TS 层 `tsc --noEmit` 通过
- 宿主集成后行为对比：加载、批注绘制、撤销/重做、持久化、导出 JSON/PDF、
  翻页、缩放、横竖屏切换（需真机，用户执行）
