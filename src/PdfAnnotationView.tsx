import { forwardRef, useImperativeHandle, useRef, type ElementRef } from 'react';
import { requireNativeComponent } from 'react-native';
import { dispatchCommand } from 'react-native/Libraries/ReactNative/RendererProxy';
import type { PdfAnnotationViewProps, PdfAnnotationViewRef } from './types';

const NativePdfAnnotationView = requireNativeComponent<PdfAnnotationViewProps>('PdfAnnotationView');

const PdfAnnotationView = forwardRef<PdfAnnotationViewRef, PdfAnnotationViewProps>(
  function PdfAnnotationView(props, ref) {
    const nativeRef = useRef<ElementRef<typeof NativePdfAnnotationView>>(null);

    useImperativeHandle(ref, () => ({
      undo() {
        dispatch(nativeRef.current, 'undo');
      },
      redo() {
        dispatch(nativeRef.current, 'redo');
      },
      clear() {
        dispatch(nativeRef.current, 'clear');
      },
      exportAnnotations(exportDir: string) {
        dispatch(nativeRef.current, 'export', [exportDir]);
      },
      setPage(pageIndex: number) {
        dispatch(nativeRef.current, 'setPage', [pageIndex]);
      },
    }));

    return <NativePdfAnnotationView {...props} ref={nativeRef} />;
  },
);

function dispatch(
  view: ElementRef<typeof NativePdfAnnotationView> | null,
  command: string,
  args: Array<string | number> = [],
) {
  if (view != null) {
    dispatchCommand(view, command, args);
  }
}

export default PdfAnnotationView;
