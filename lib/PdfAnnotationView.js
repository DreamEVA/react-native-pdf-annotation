"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const jsx_runtime_1 = require("react/jsx-runtime");
const react_1 = require("react");
const react_native_1 = require("react-native");
const RendererProxy_1 = require("react-native/Libraries/ReactNative/RendererProxy");
const NativePdfAnnotationView = (0, react_native_1.requireNativeComponent)('PdfAnnotationView');
const PdfAnnotationView = (0, react_1.forwardRef)(function PdfAnnotationView(props, ref) {
    const nativeRef = (0, react_1.useRef)(null);
    (0, react_1.useImperativeHandle)(ref, () => ({
        undo() {
            dispatch(nativeRef.current, 'undo');
        },
        redo() {
            dispatch(nativeRef.current, 'redo');
        },
        clear() {
            dispatch(nativeRef.current, 'clear');
        },
        exportAnnotations(exportDir) {
            dispatch(nativeRef.current, 'export', [exportDir]);
        },
        setPage(pageIndex) {
            dispatch(nativeRef.current, 'setPage', [pageIndex]);
        },
    }));
    return (0, jsx_runtime_1.jsx)(NativePdfAnnotationView, { ...props, ref: nativeRef });
});
function dispatch(view, command, args = []) {
    if (view != null) {
        (0, RendererProxy_1.dispatchCommand)(view, command, args);
    }
}
exports.default = PdfAnnotationView;
