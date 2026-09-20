import { requireNativeComponent } from 'react-native';
import type { PdfAnnotationViewProps } from './types';

const PdfAnnotationView = requireNativeComponent<PdfAnnotationViewProps>('PdfAnnotationView');

export default PdfAnnotationView;
