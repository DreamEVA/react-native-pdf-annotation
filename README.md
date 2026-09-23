# react-native-pdf-annotation

Android 端 PDF 手写批注原生组件（Kotlin 实现），用于在 PDF 上自由书写标注、撤销/重做、自动保存，并把批注导出为 JSON。

- 渲染引擎：AndroidPdfViewer 3.2.0-beta.1（pdfium，与 react-native-pdf 同源）
- 支持 React Native 0.72+，旧架构与新架构（含 bridgeless）双兼容

## 特性

- 在 PDF 任意页面手写批注，坐标按页面归一化存储；缩放或换屏后笔迹位置仍与内容对齐。线宽按绘制时的屏幕像素保存，换屏后粗细不会按新屏幕重算
- 单指绘制、多指缩放/滚动。双击在三档之间循环：中档 `(minScale + maxScale) / 2` → `maxScale` → 初始 `scale`
- 全局跨页撤销 / 重做（上限 100 笔）
- 批注自动保存（300ms 防抖 + 原子写入），重新打开自动恢复
- 导出批注 JSON
- 目录（table of contents）回调、页码回调、加载状态回调
- App 后台 / 内存压力时自动释放 pdfium 原生资源，防止崩溃

> 依赖说明：android-pdf-viewer 公开发布的最高版本为 `3.2.0-beta.1`，且仅发布在已关闭的
> JCenter 上。本库已将其与 pdfium-android 的 jar，以及 4 个主流 ABI
> （`arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`）的原生库直接内置进包。这些产物不引用
> 旧版 `android.support`，**宿主无需配置任何额外 Maven 仓库，也无需启用 jetifier**。
> 包内不含 `libc++_shared.so`。React Native 0.72 起宿主已经带了这份 C++ 运行库，pdfium 运行时用宿主的那一份。
>
> 内置的 pdfium 为 2018 年的 `pdfium-android` 1.9.0，不包含此后的上游安全修复。
> 本库仅打开设备上的本地 PDF，不发起网络请求。

## 环境要求

| 项目 | 要求 |
|---|---|
| React Native | ≥ 0.72（新架构/旧架构均可） |
| JDK | 17+（RN 0.72/0.73 老工程若仍用 JDK 11，需升级或设置 `javaTargetVersion=11`） |
| AGP | ≥ 7.3 |
| Kotlin | 默认 1.8.22。不会自动跟随宿主的 Kotlin 版本，需要一致时设置 `kotlinVersion`（见下文） |

## 安装

```bash
npm install react-native-pdf-annotation
# 或安装本地打包产物
npm install ./react-native-pdf-annotation-1.0.0.tgz
```

autolinking 会自动注册原生模块，无需手动配置 gradle。

## 示例

演示工程只在 Git 仓库的 `example/` 里，npm 包不包含这个目录。`MainActivity` 已接入生命周期回调。

```bash
git clone https://github.com/DreamEVA/react-native-pdf-annotation.git
cd react-native-pdf-annotation/example
npm install
npm run android
```

修改库的 TypeScript 后，在仓库根目录执行 `npm run build`。示例通过构建产物 `lib/` 引用该库。

### 必须配置：Activity 生命周期分发

组件在 Activity 进入后台或系统内存压力时会主动回收 pdfium 原生资源，**必须在
MainActivity 中转发 4 个生命周期回调**，否则可能出现原生崩溃。

Kotlin 版 `MainActivity.kt`：

```kotlin
import com.reactnativepdfannotation.PdfAnnotationLifecycle

class MainActivity : ReactActivity() {
    override fun onPause() {
        super.onPause()
        PdfAnnotationLifecycle.onHostPause(this)
    }

    override fun onStop() {
        super.onStop()
        PdfAnnotationLifecycle.onHostStop(this)
    }

    override fun onResume() {
        super.onResume()
        PdfAnnotationLifecycle.onHostResume(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        PdfAnnotationLifecycle.onTrimMemory(this, level)
    }
}
```

Java 版 `MainActivity.java`：

```java
import com.reactnativepdfannotation.PdfAnnotationLifecycle;

@Override protected void onPause() { super.onPause(); PdfAnnotationLifecycle.onHostPause(this); }
@Override protected void onStop() { super.onStop(); PdfAnnotationLifecycle.onHostStop(this); }
@Override protected void onResume() { super.onResume(); PdfAnnotationLifecycle.onHostResume(this); }
@Override public void onTrimMemory(int level) { super.onTrimMemory(level); PdfAnnotationLifecycle.onTrimMemory(this, level); }
```

> 宿主工程中若仍保留原生视图名同为 `PdfAnnotationView` 的旧实现，需先删除，否则 `REACT_CLASS` 冲突。

### 可选：gradle 属性覆盖

均通过宿主 root `build.gradle` 的 `ext` 或 `gradle.properties` 覆盖：

| 属性 | 默认值 | 说明 |
|---|---|---|
| `kotlinVersion` | 1.8.22 | Kotlin 插件版本 |
| `compileSdkVersion` | 34 | 编译 SDK |
| `minSdkVersion` | 23 | 最低 SDK |
| `targetSdkVersion` | 34 | 目标 SDK |
| `javaTargetVersion` | 17 | Java/Kotlin 目标版本，需与宿主一致 |
| `reactNativeVersion` | `+` | react-android 版本。宿主构建时由 React Native Gradle 插件锁定 |

## 快速开始

```tsx
import React, { useRef } from 'react';
import PdfAnnotationView, { type PdfAnnotationViewRef } from 'react-native-pdf-annotation';

export default function PdfEditor() {
  const pdfRef = useRef<PdfAnnotationViewRef>(null);

  return (
    <PdfAnnotationView
      ref={pdfRef}
      style={{ flex: 1 }}
      filePath={`file://${privatePdfPath}`}      // 实际加载的 PDF 路径
      originalPath={`file://${publicPdfPath}`}   // 公共目录原始路径（决定批注存哪，见下文）
      annotationMode={true}                       // true = 手写模式；false = 纯阅读模式
      strokeColor="#FF0000"
      strokeWidth={5}
      scale={1}
      minScale={1}
      maxScale={5}
      onLoadComplete={({ nativeEvent }) => {
        // { pageCount, filePath, width, height }
        console.log('loaded', nativeEvent.pageCount);
      }}
      onPageChanged={({ nativeEvent }) => {
        // { page, pageCount }，page 为 0 基索引
        console.log('page', nativeEvent.page + 1);
      }}
      onError={({ nativeEvent }) => console.warn(nativeEvent.message)}
      onAnnotationChanged={({ nativeEvent }) => {
        // nativeEvent.data：当前全部批注的 JSON 字符串（归一化格式，见下文）
        saveToServer(nativeEvent.data);
      }}
    />
  );
}
```

## Props

| prop | 类型 | 默认 | 说明 |
|---|---|---|---|
| `filePath` | string | - | 实际加载的 PDF 文件路径（`file://` 或绝对路径），赋值即触发加载 |
| `originalPath` | string | - | PDF 的原始存放路径，**决定批注文件写入位置**（详见下文） |
| `annotationMode` | boolean | false | `true` 时单指绘制批注，多指仍可缩放/滚动；`false` 为纯阅读模式 |
| `strokeColor` | string | 红色 | 画笔颜色，如 `#FF0000` |
| `strokeWidth` | number | 5 | 画笔宽度（屏幕像素） |
| `scale` | number | 1 | 初始缩放 |
| `minScale` | number | 1 | 最小缩放 |
| `maxScale` | number | 5 | 最大缩放 |

## 命令

通过组件 ref 调用。命令经 React Native `dispatchCommand` 派发，适用于旧架构与新架构。

| 方法 | 参数 | 说明 |
|---|---|---|
| `undo()` | - | 撤销最近一笔（全局跨页） |
| `redo()` | - | 重做 |
| `clear()` | - | 清空全部批注，并删除磁盘上的批注文件 |
| `exportAnnotations(exportDir)` | 导出目录 | 导出批注 JSON。文件名取**正在加载的 PDF**（`filePath`）的文件名，加 `.ann.json`。`width` 为相对视图宽度的比例，与 `onAnnotationChanged` 相同，不是磁盘 `.ann.json` 的像素格式。成功时 `onExportResult` 的 `message` 为导出文件绝对路径 |
| `setPage(pageIndex)` | 0 基页码 | 跳转到指定页 |

```ts
pdfRef.current?.undo();
pdfRef.current?.setPage(2); // 第 3 页
pdfRef.current?.exportAnnotations('/sdcard/Documents/annotations');
```

## 事件

事件数据均位于 `event.nativeEvent`：

| 事件 | 载荷 | 说明 |
|---|---|---|
| `onLoadComplete` | `pageCount, filePath, width, height` | PDF 加载完成 |
| `onPageChanged` | `page, pageCount` | 翻页回调，`page` 为 **0 基**索引 |
| `onTableOfContents` | `filePath, tableOfContents` | 目录回调，`tableOfContents` 为 `[{title, page, pageNumber, children}]` 树 |
| `onError` | `message` | 加载/渲染错误 |
| `onAnnotationChanged` | `data` | 批注变化时回调，`data` 为归一化 JSON 字符串 |
| `onExportResult` | `success, message` | `exportAnnotations` 的结果。成功时 `message` 为导出文件绝对路径，失败时为错误信息 |

> TypeScript 类型均已导出：`import PdfAnnotationView, { type PdfAnnotationViewProps, type PdfAnnotationViewRef } from 'react-native-pdf-annotation'`。

## 批注存储

### 存储规则

组件会在批注变化后 **300ms 防抖**自动把全部批注序列化为 JSON，写到磁盘文件：

```
批注文件路径 = (originalPath ?? filePath) 去掉 "file://" 前缀 + ".ann.json"
```

即：**优先以 `originalPath` 为基准，未传 `originalPath` 时以 `filePath` 为基准**，批注文件与
对应 PDF 同名、同目录，后缀 `.ann.json`。

### 只传 filePath

```tsx
filePath="file:///data/user/0/com.example/files/foo.pdf"
// 批注文件 → /data/user/0/com.example/files/foo.pdf.ann.json
```

批注文件与 PDF 位于同一目录。`filePath` 指向应用私有目录时，批注随应用卸载删除。

### 同时传 originalPath（推荐）

```tsx
filePath="file:///data/user/0/com.example/files/foo.pdf"      // 复制到私有目录的副本，供组件加载
originalPath="file:///storage/emulated/0/Documents/foo.pdf"  // 公共目录中的原始 PDF
// 批注文件 → /storage/emulated/0/Documents/foo.pdf.ann.json
```

PDF 被复制到私有目录时，批注仍写在 `originalPath` 旁：

- `originalPath` 位于公共目录时，卸载应用不会删除批注
- 批注文件与原始 PDF 同目录，文件名为 PDF 文件名加 `.ann.json`。删除 PDF 时不会自动删除该文件
- 再次打开同一 `originalPath` 时，从 `.ann.json` 恢复批注与撤销栈。重做栈不恢复

### 写入可靠性与格式

- **原子写入**：先写临时文件再 `rename`。`rename` 失败时改为直接写入目标文件
- **损坏的 JSON**：解析失败时当作没有批注，从空白开始，原文件保留且不会改名为 `.corrupt`。之后一旦有新笔迹并触发自动保存，会覆盖这个坏文件。只有读取文件本身抛出异常时，才会把原文件备份为 `.corrupt` 再从空白开始
- **格式版本** `version=3`，结构如下：

```json
{
  "version": 3,
  "pdfPath": "/storage/emulated/0/Documents/foo.pdf",
  "savedAt": 1751234567890,
  "pages": [
    {
      "pageIndex": 0,
      "strokes": [
        {
          "color": "#FFFF0000",
          "width": 5,
          "timestamp": 1751234567890,
          "points": [{ "x": 0.12, "y": 0.34 }, { "x": 0.15, "y": 0.36 }]
        }
      ]
    }
  ]
}
```

- `points` 为 **0~1 归一化页面坐标**，所以笔迹位置在任何屏幕尺寸和缩放下都与 PDF 内容对齐。线宽不参与这套归一化（见下条）
- `width` 在 version 3 中为屏幕像素。读取时只有 version 2 会把 `width` 乘以当前视图宽度换回像素；version 1 按文件中的原值当作像素使用
- `clear` 命令会删除磁盘上的批注文件

### onAnnotationChanged 的 data 与磁盘文件区别

`onAnnotationChanged` 的 `data` 与 `exportAnnotations` 写出的 JSON 使用归一化线宽（相对视图宽度的比例）。磁盘 `.ann.json` 中的 `width` 为屏幕像素。两者字段结构相同，仅 `width` 的单位不同。

## 存储权限

组件本身**不申请任何权限**，由宿主 App 负责。是否需要权限取决于 PDF/批注文件所在目录：

| 文件位置 | 需要权限 |
|---|---|
| 应用私有目录（`filesDir`） | 无 |
| 应用专属外部目录（`getExternalFilesDir`） | 无（但随卸载删除） |
| 公共目录（如 `/storage/emulated/0/Documents/`，Android 10 及以下） | `WRITE_EXTERNAL_STORAGE`（运行时申请） |
| 公共目录（Android 11+ / API 30+） | `MANAGE_EXTERNAL_STORAGE`（"所有文件访问"，特殊设置页授权） |

`originalPath` 指向公共目录时，宿主工程需要完成以下配置。

### 1. AndroidManifest.xml 声明

```xml
<!-- Android 10 及以下：传统存储权限 -->
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />

<!-- Android 11+：所有文件访问权限 -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />

<application
    android:requestLegacyExternalStorage="true"
    ... >
```

`requestLegacyExternalStorage="true"` 让 Android 10 沿用传统存储模型（Android 11+ 忽略此属性）。

### 2. 运行时协商（低版本弹窗 / 高版本跳设置页）

```kotlin
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

private fun ensureStoragePermission(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Android 11+：必须引导用户到"所有文件访问"设置页手动授权
        if (!Environment.isExternalStorageManager()) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
    } else {
        // Android 10 及以下：普通运行时权限弹窗
        if (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE
            )
        }
    }
}
```

授权结果回调：

```kotlin
override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<out String>, grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_STORAGE &&
        grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
    ) {
        // 授权成功后重新加载 PDF / 刷新组件
    }
}
```

### 注意事项

- `MANAGE_EXTERNAL_STORAGE` 在 Google Play 上属于受限权限，需声明用途并通过审核。仅需随应用保存批注时，使用应用专属外部目录 `context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)`，无需该权限，数据随卸载删除。
- 部分系统对「所有文件访问」的入口名称不同，需在系统设置中授权。
- 在加载 PDF 之前完成授权，避免组件因无权限无法读写文件。

## 常见问题

**Q：不配置 PdfAnnotationLifecycle 会怎样？**
A：应用进入后台或系统回收内存后，pdfium 原生资源可能已释放，再次绘制会触发原生崩溃（SIGSEGV）。需接入上文 `MainActivity` 的 4 个生命周期回调。

**Q：RN 0.72/0.73 工程构建报 `invalid target release 17`？**
A：构建 JDK 低于 17 所致。升级 JDK 17，或在宿主 gradle 设置 `ext.javaTargetVersion = "11"`。

**Q：批注在换设备/换屏幕后位置还对吗？**
A：位置对。笔迹坐标是页面归一化值，与分辨率无关。线宽保存的是绘制时的屏幕像素，换到更宽或更窄的屏幕后，相对页面的粗细会变。
