# react-native-pdf-annotation

Kotlin 版 PDF 手写批注原生组件（Android）。基于 AndroidPdfViewer 3.2.0-beta.1（pdfium）+ iText7，
支持 RN 0.72+（旧架构/新架构 interop 兼容）。

> 依赖说明：android-pdf-viewer 公开发布的最高版本为 `3.2.0-beta.1`，且只发布在已关闭的
> JCenter 上。本库已将 android-pdf-viewer 与 pdfium-android 的产物（已 jetify 为 androidx
> 引用、仅含 4 个主流 ABI 的原生库）直接内置进包，**宿主无需配置任何额外 Maven 仓库**，
> 也无需启用 jetifier。

## 安装

要求：RN ≥ 0.72（AGP ≥ 7.3，Kotlin 1.8+）。本包面向新工程使用，无需改动任何旧 Java 代码。

```bash
npm install react-native-pdf-annotation
# 或本地路径
npm install ../ReactNativePDF_handwritten
```

autolinking 自动注册原生模块（ReactNative CLI ≥ 0.60）。

### 宿主工程必须的改动

1. 如宿主工程内曾内嵌过同名旧组件（native 视图名 `PdfAnnotationView`），请先删除旧
   Java/Kotlin 源码，避免 REACT_CLASS 冲突（全新工程无需此步）。
2. `MainActivity` 接入生命周期分发：

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

3. gradle 可选覆盖（root build.gradle ext 或 gradle.properties）：`kotlinVersion`（默认 1.8.22）、
   `compileSdkVersion`（默认 34）、`minSdkVersion`（默认 23）、`targetSdkVersion`（默认 34）、
   `reactNativeVersion`（react-android 版本，默认 `+`，宿主构建中由 RN 插件自动锁定为宿主版本，通常无需设置）。

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
| strokeColor | string | 红色(RED) | 画笔颜色，如 `#FF0000` |
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
