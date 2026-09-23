# react-native-pdf-annotation

Android 端 PDF 手写批注原生组件（Kotlin 实现），用于在 PDF 上自由书写标注、撤销/重做、自动保存，并支持把批注导出为 JSON 或烘焙进 PDF 文件。

- 渲染引擎：AndroidPdfViewer 3.2.0-beta.1（pdfium，与 react-native-pdf 同源）
- 导出引擎：iText7
- 支持 React Native 0.72+，旧架构与新架构（含 bridgeless）双兼容

## 特性

- 在 PDF 任意页面手写批注，坐标按页面归一化存储，缩放/换屏后批注始终与内容对齐
- 单指绘制、多指缩放/滚动、双击三档缩放
- 全局跨页撤销 / 重做（上限 100 笔）
- 批注自动保存（300ms 防抖 + 原子写入），重新打开自动恢复
- 导出批注 JSON，或通过 iText7 将批注永久烘焙进 PDF
- 目录（table of contents）回调、页码回调、加载状态回调
- App 后台 / 内存压力时自动释放 pdfium 原生资源，防止崩溃

> 依赖说明：android-pdf-viewer 公开发布的最高版本为 `3.2.0-beta.1`，且仅发布在已关闭的
> JCenter 上。本库已将其与 pdfium-android 的产物（已 jetify 为 androidx 引用、仅保留 4 个
> 主流 ABI）直接内置进包，**宿主无需配置任何额外 Maven 仓库，也无需启用 jetifier**。

## 环境要求

| 项目 | 要求 |
|---|---|
| React Native | ≥ 0.72（新架构/旧架构均可） |
| JDK | 17+（RN 0.72/0.73 老工程若仍用 JDK 11，需升级或设置 `javaTargetVersion=11`） |
| AGP | ≥ 7.3 |
| Kotlin | 1.8+（随宿主自动使用其 Kotlin 版本） |

## 安装

```bash
npm install react-native-pdf-annotation
# 或安装本地打包产物
npm install ./react-native-pdf-annotation-1.0.0.tgz
```

autolinking 会自动注册原生模块，无需手动配置 gradle。

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

> 若宿主工程内曾内嵌过同名旧组件（原生视图名 `PdfAnnotationView`），请先删除旧源码，
> 避免 REACT_CLASS 冲突（全新工程无需此步）。

### 可选：gradle 属性覆盖

均通过宿主 root `build.gradle` 的 `ext` 或 `gradle.properties` 覆盖：

| 属性 | 默认值 | 说明 |
|---|---|---|
| `kotlinVersion` | 1.8.22 | Kotlin 插件版本 |
| `compileSdkVersion` | 34 | 编译 SDK |
| `minSdkVersion` | 23 | 最低 SDK |
| `targetSdkVersion` | 34 | 目标 SDK |
| `javaTargetVersion` | 17 | Java/Kotlin 目标版本，需与宿主一致 |
| `reactNativeVersion` | `+` | react-android 版本；宿主构建中由 RN 插件自动锁定，通常无需设置 |

## 快速开始

```tsx
import React, { useRef, useCallback } from 'react';
import { UIManager, findNodeHandle } from 'react-native';
import PdfAnnotationView from 'react-native-pdf-annotation';

export default function PdfEditor() {
  const pdfRef = useRef(null);

  const dispatch = useCallback((command: string, args: unknown[] = []) => {
    const node = findNodeHandle(pdfRef.current);
    if (node != null) {
      UIManager.dispatchViewManagerCommand(node, command, args);
    }
  }, []);

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

通过 `UIManager.dispatchViewManagerCommand(node, command, args)` 调用：

| 命令 | 参数 | 说明 |
|---|---|---|
| `undo` | - | 撤销最近一笔（全局跨页） |
| `redo` | - | 重做 |
| `clear` | - | 清空全部批注，并删除磁盘上的批注文件 |
| `export` | `[exportDir]` | 导出批注 JSON 到目录（文件名为 `{pdf文件名}.ann.json`），结果回调 `onExportResult` |
| `exportPdf` | `[exportPath]` | 把批注永久烘焙进 PDF 并输出到指定路径，结果回调 `onExportPdfResult` |
| `setPage` | `[pageIndex]` | 跳转到指定页（0 基索引） |

```ts
// 撤销
UIManager.dispatchViewManagerCommand(handle, 'undo', []);
// 跳转到第 3 页
UIManager.dispatchViewManagerCommand(handle, 'setPage', [2]);
// 导出批注 JSON
UIManager.dispatchViewManagerCommand(handle, 'export', ['/sdcard/Documents/annotations']);
// 烘焙导出 PDF
UIManager.dispatchViewManagerCommand(handle, 'exportPdf', ['/sdcard/Documents/out.pdf']);
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
| `onExportResult` | `success, message` | `export` 命令结果 |
| `onExportPdfResult` | `success, message, filePath?` | `exportPdf` 命令结果 |

> TypeScript 类型均已导出：`import PdfAnnotationView, { PdfAnnotationViewProps } from 'react-native-pdf-annotation'`。

## 批注数据如何存储

这是使用本组件最需要理解的部分。

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

批注文件就写在 PDF 旁边。**注意**：如果 `filePath` 指向应用私有目录，批注会随 App
卸载一起被删除。

### 同时传 originalPath（推荐）

```tsx
filePath="file:///data/user/0/com.example/files/foo.pdf"      // 复制到私有目录的副本，供组件加载
originalPath="file:///storage/emulated/0/Documents/foo.pdf"  // 公共目录中的原始 PDF
// 批注文件 → /storage/emulated/0/Documents/foo.pdf.ann.json
```

即使 PDF 本体被复制到私有目录，批注也固定写在 `originalPath` 旁，好处：

- **卸载 App 不丢失**（批注在公共目录）
- 删除原始 PDF 时，批注文件随同名文件一起清理
- 重新打开同一 PDF 时自动从 `.ann.json` 恢复批注与撤销栈

### 写入可靠性与格式

- **原子写入**：先写临时文件再 `rename`，崩溃不会损坏既有批注
- **异常自愈**：JSON 损坏时自动备份为 `.corrupt` 并从空白开始，不影响加载
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

- `points` 为 **0~1 归一化页面坐标**，因此批注在任何屏幕尺寸/缩放下都与 PDF 内容对齐
- `width` 为屏幕像素（version 3；旧版本文件会自动兼容转换）
- `clear` 命令会删除磁盘上的批注文件

### onAnnotationChanged 的 data 与磁盘文件区别

`onAnnotationChanged` 回调中的 `data` 是**归一化 JSON**（`width` 为相对视图宽度的比例），
供 JS 侧展示/上传；磁盘上的 `.ann.json` 则是 `width` 为屏幕像素的持久化格式。两者都
由同一份批注数据派生，字段结构一致。

## 存储权限

组件本身**不申请任何权限**，由宿主 App 负责。是否需要权限取决于 PDF/批注文件所在目录：

| 文件位置 | 需要权限 |
|---|---|
| 应用私有目录（`filesDir`） | 无 |
| 应用专属外部目录（`getExternalFilesDir`） | 无（但随卸载删除） |
| 公共目录（如 `/storage/emulated/0/Documents/`，Android 10 及以下） | `WRITE_EXTERNAL_STORAGE`（运行时申请） |
| 公共目录（Android 11+ / API 30+） | `MANAGE_EXTERNAL_STORAGE`（"所有文件访问"，特殊设置页授权） |

如果你把 `originalPath` 指向公共目录，必须在宿主工程完成以下两步。

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

### 建议与注意事项

- **上架 Google Play**：`MANAGE_EXTERNAL_STORAGE` 属于受限权限，需要声明具体用途并经
  官方审核，可能被拒。若仅需暂存批注，推荐改用**应用专属外部目录**
  （`context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)`），完全免权限，但
  数据随卸载删除；如需"卸载不丢"，再考虑公共目录 + `MANAGE_EXTERNAL_STORAGE`。
- **鸿蒙/国产 ROM**：部分机型对"所有文件访问"入口有差异（如"文件管理权限"），需在
  系统设置中手动开启；组件无法代替用户完成。
- 权限申请时机建议在**打开 PDF 前**完成（如点击打开文档时先 `ensureStoragePermission`，
  授权后再渲染组件），避免组件因无权限读不到文件或写不了批注。

## 许可证说明

本组件依赖 iText7（AGPL 许可）。若以开源形式发布，AGPL 兼容；**商用闭源发布前请评估
iText7 许可证合规**（必要时购买商业许可或替换导出实现）。

## 常见问题

**Q：不配置 PdfAnnotationLifecycle 会怎样？**
A：后台/内存压力时 pdfium 原生资源可能被系统回收，组件再绘制会访问已释放内存导致
原生崩溃（SIGSEGV）。请务必接入（见上文 4 行代码）。

**Q：RN 0.72/0.73 工程构建报 `invalid target release 17`？**
A：构建 JDK 低于 17 所致。升级 JDK 17，或在宿主 gradle 设置 `ext.javaTargetVersion = "11"`。

**Q：批注在换设备/换屏幕后位置还对吗？**
A：对。批注以页面归一化坐标存储，与设备分辨率无关。
