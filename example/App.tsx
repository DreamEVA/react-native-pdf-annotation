/**
 * react-native-pdf-annotation 完整测试 DEMO
 *
 * 覆盖 README.md 中的全部能力：
 *  - PDF 选择与加载（filePath）
 *  - 手写批注模式开关（annotationMode）
 *  - 画笔颜色 / 线宽（strokeColor / strokeWidth）
 *  - 缩放范围（scale / minScale / maxScale）
 *  - 命令：undo / redo / clear / exportAnnotations / setPage
 *  - 事件：onLoadComplete / onPageChanged / onTableOfContents /
 *          onAnnotationChanged / onError / onExportResult
 *
 * 选文件由示例内的 PdfDocumentPicker 完成，并复制到应用缓存中按来源固定的路径。
 * filePath 即该路径，批注写在 PDF 旁，随卸载删除。需在卸载后保留时，将 filePath 指向已授权的公共目录。
 */

import { useEffect, useRef, useState } from 'react';
import {
  Button,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  NativeModules,
  View,
  useColorScheme,
} from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import PdfAnnotationView, { type PdfAnnotationViewRef } from 'react-native-pdf-annotation';

type PickedPdf = { uri: string; name: string; localUri: string };

const PdfDocumentPicker = NativeModules.PdfDocumentPicker as {
  pick: () => Promise<PickedPdf>;
};

// 画笔颜色预设
const COLORS = [
  { label: '红', value: '#FF0000' },
  { label: '黑', value: '#000000' },
  { label: '蓝', value: '#0000FF' },
  { label: '绿', value: '#00AA00' },
  { label: '橙', value: '#FF8800' },
];

// 线宽预设（屏幕像素）
const WIDTHS = [1, 3, 5, 8, 12];

/** 统计批注 JSON 中的笔迹数量，供 onAnnotationChanged 日志使用。 */
function countStrokes(data: string): number {
  try {
    const obj = JSON.parse(data);
    const pages: Array<{ strokes?: unknown[] }> = obj?.pages ?? [];
    return pages.reduce((sum, p) => sum + (p.strokes?.length ?? 0), 0);
  } catch {
    return -1;
  }
}

/** 返回 file:// 路径所在目录，供导出批注到 PDF 同目录。 */
function dirname(fileUrl: string): string {
  const p = fileUrl.replace(/^file:\/\//, '');
  const idx = p.lastIndexOf('/');
  return idx >= 0 ? p.slice(0, idx) : p;
}

function App() {
  const isDarkMode = useColorScheme() === 'dark';
  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <Demo />
    </SafeAreaProvider>
  );
}

function Demo() {
  const pdfRef = useRef<PdfAnnotationViewRef>(null);

  // 文档
  const [pdfPath, setPdfPath] = useState<string | null>(null);
  const [fileName, setFileName] = useState('');

  // 画笔 / 缩放
  const [annotationMode, setAnnotationMode] = useState(true);
  const [strokeColor, setStrokeColor] = useState('#FF0000');
  const [strokeWidth, setStrokeWidth] = useState(5);
  const [scale, setScale] = useState(1);
  const [minScale, setMinScale] = useState(0.5);
  const [maxScale, setMaxScale] = useState(5);

  // 页面 / 状态
  const [page, setPage] = useState(0);
  const [pageCount, setPageCount] = useState(0);
  const [jumpInput, setJumpInput] = useState('1');
  const [error, setError] = useState<string | null>(null);
  const [exportResult, setExportResult] = useState('');
  const [logs, setLogs] = useState<string[]>([]);

  useEffect(() => {
    console.log(error);
  },[error])

  const addLog = (msg: string) => setLogs(prev => [msg, ...prev].slice(0, 8));

  const pickPdf = async () => {
    try {
      const file = await PdfDocumentPicker.pick();
      // localUri 为百分号编码。原生层只去掉 file:// 前缀，不解码，需先得到真实路径。
      const localPath = decodeURI(file.localUri);
      setPdfPath(localPath);
      setFileName(file.name ?? 'document.pdf');
      setPage(0);
      setPageCount(0);
      setError(null);
      setExportResult('');
      setLogs([]);
      addLog('已选择：' + (file.name ?? 'document.pdf'));
    } catch (err) {
      const message = String(err);
      if (message.includes('OPERATION_CANCELED')) {
        return;
      }
      setError(message);
    }
  };

  // ---------- 命令 ----------
  const undo = () => pdfRef.current?.undo();
  const redo = () => pdfRef.current?.redo();
  const clear = () => pdfRef.current?.clear();
  const exportAnnotations = () => {
    if (pdfPath) {
      pdfRef.current?.exportAnnotations(dirname(pdfPath));
    }
  };
  const jumpToPage = () => {
    const n = parseInt(jumpInput, 10);
    if (Number.isFinite(n) && n >= 1 && n <= pageCount) {
      pdfRef.current?.setPage(n - 1); // 组件内部为 0 基
    }
  };

  const renderLog = (logs: string[]) => logs.map((l, i) => (
    <Text key={i} style={styles.logLine} numberOfLines={1}>
      {l}
    </Text>
  ));

  return (
    <View style={styles.root}>
      {/* 标题栏 */}
      <View style={styles.header}>
        <Text style={styles.title}>PDF 手写批注 DEMO</Text>
        <Text style={styles.fileName} numberOfLines={1}>
          {fileName || '尚未选择文档'}
        </Text>
      </View>

      {/* 状态栏 */}
      <View style={styles.statusBar}>
        <Text style={styles.statusText}>
          第 {pageCount ? page + 1 : 0} / {pageCount} 页
        </Text>
        {exportResult ? (
          <Text style={styles.exportResult} numberOfLines={1}>
            {exportResult}
          </Text>
        ) : null}
        {error ? (
          <Text style={styles.error} numberOfLines={5}>
            错误：{error}
          </Text>
        ) : null}
      </View>

      {/* PDF 视图 */}
      <View style={styles.pdfWrap}>
        {pdfPath ? (
          <PdfAnnotationView
            ref={pdfRef}
            style={StyleSheet.absoluteFill}
            filePath={pdfPath}
            annotationMode={annotationMode}
            strokeColor={strokeColor}
            strokeWidth={strokeWidth}
            scale={scale}
            minScale={minScale}
            maxScale={maxScale}
            onLoadComplete={({ nativeEvent }) => {
              setPageCount(nativeEvent.pageCount);
              setPage(0);
              setError(null);
              addLog(
                `加载完成：${nativeEvent.pageCount} 页（${nativeEvent.width}x${nativeEvent.height}）`,
              );
            }}
            onPageChanged={({ nativeEvent }) => {
              setPage(nativeEvent.page);
              addLog(`翻页：${nativeEvent.page + 1} / ${nativeEvent.pageCount}`);
            }}
            onTableOfContents={({ nativeEvent }) => {
              const toc = nativeEvent.tableOfContents ?? [];
              addLog(`目录：共 ${toc.length} 项`);
              toc.slice(0, 3).forEach(item => addLog(`  · ${item.title}`));
            }}
            onError={({ nativeEvent }) => {
              setError(nativeEvent.message);
              addLog('错误：' + nativeEvent.message);
            }}
            onAnnotationChanged={({ nativeEvent }) => {
              const n = countStrokes(nativeEvent.data);
              addLog(
                n >= 0 ? `批注变化：共 ${n} 笔` : '批注变化：收到数据（解析失败）',
              );
            }}
            onExportResult={({ nativeEvent }) => {
              const msg = nativeEvent.success
                ? '导出成功：' + nativeEvent.message
                : '导出失败：' + nativeEvent.message;
              setExportResult(msg);
              addLog(msg);
            }}
          />
        ) : (
          <View style={styles.empty}>
            <Text style={styles.hint}>请选择一个 PDF 文档开始测试</Text>
            <Button title="选择 PDF 文档" onPress={pickPdf} />
          </View>
        )}
      </View>

      {/* 控制面板 */}
      <View style={styles.panel}>
        {/* 主操作 */}
        <View style={styles.toolbar}>
          <Button title={annotationMode ? '批注：开' : '批注：关'} onPress={() => setAnnotationMode(v => !v)} />
          <Button title="撤销" onPress={undo} />
          <Button title="重做" onPress={redo} />
          <Button title="清空" onPress={clear} />
          <Button title="导出" onPress={exportAnnotations} />
          <Button title="重新选择" onPress={pickPdf} />
        </View>

        {/* 颜色 + 线宽 */}
        <View style={styles.toolbar}>
          <Text style={styles.label}>颜色</Text>
          {COLORS.map(c => {
            const active = c.value === strokeColor;
            return (
              <Pressable
                key={c.value}
                onPress={() => setStrokeColor(c.value)}
                style={[styles.swatch, { backgroundColor: c.value }, active && styles.swatchActive]}
              >
                <Text style={styles.swatchText}>{c.label}</Text>
              </Pressable>
            );
          })}
          <Text style={styles.label}>线宽</Text>
          {WIDTHS.map(w => (
            <Pressable
              key={w}
              onPress={() => setStrokeWidth(w)}
              style={[styles.widthChip, w === strokeWidth && styles.widthChipActive]}
            >
              <Text style={styles.widthChipText}>{w}</Text>
            </Pressable>
          ))}
        </View>

        {/* 翻页 + 缩放 */}
        <View style={styles.toolbar}>
          <Button title="上一页" onPress={() => pdfRef.current?.setPage(Math.max(0, page - 1))} />
          <Button title="下一页" onPress={() => pdfRef.current?.setPage(Math.min(pageCount - 1, page + 1))} />
          <TextInput
            style={styles.input}
            value={jumpInput}
            onChangeText={setJumpInput}
            keyboardType="number-pad"
            placeholder="页码"
          />
          <Button title="跳转" onPress={jumpToPage} />
          <Text style={styles.label}>
            缩放 {scale}（{minScale}~{maxScale}）
          </Text>
        </View>

        {/* 事件日志 */}
        <ScrollView style={styles.log} contentContainerStyle={styles.logContent}>
          {renderLog(logs)}
        </ScrollView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#f2f2f2' },
  header: {
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 4,
    backgroundColor: '#fff',
  },
  title: { fontSize: 18, fontWeight: '700', color: '#222' },
  fileName: { fontSize: 12, color: '#888', marginTop: 2 },
  statusBar: {
    paddingHorizontal: 16,
    paddingVertical: 4,
    backgroundColor: '#fff',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderColor: '#ddd',
  },
  statusText: { fontSize: 13, color: '#444' },
  exportResult: { fontSize: 12, color: '#2e7d32', marginTop: 2 },
  error: { fontSize: 12, color: '#c62828', marginTop: 2 },
  pdfWrap: { flex: 1, backgroundColor: '#ccc' },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  hint: { fontSize: 15, color: '#555', marginBottom: 12 },
  panel: { backgroundColor: '#fff', borderTopWidth: StyleSheet.hairlineWidth, borderColor: '#ddd' },
  toolbar: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 8,
    paddingVertical: 6,
  },
  label: { fontSize: 13, color: '#555', marginHorizontal: 4 },
  swatch: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  swatchActive: { borderColor: '#000' },
  swatchText: { fontSize: 12, color: '#fff' },
  widthChip: {
    minWidth: 30,
    paddingHorizontal: 8,
    paddingVertical: 6,
    borderRadius: 15,
    backgroundColor: '#eee',
    alignItems: 'center',
  },
  widthChipActive: { backgroundColor: '#1976d2' },
  widthChipText: { fontSize: 13, color: '#333' },
  input: {
    width: 64,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 4,
    fontSize: 14,
  },
  log: { maxHeight: 88, backgroundColor: '#fafafa', borderTopWidth: StyleSheet.hairlineWidth, borderColor: '#ddd' },
  logContent: { paddingHorizontal: 12, paddingVertical: 6 },
  logLine: { fontSize: 12, color: '#666', lineHeight: 18 },
});

export default App;
