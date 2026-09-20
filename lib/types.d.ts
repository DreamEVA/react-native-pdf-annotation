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
export type PdfAnnotationCommand = 'undo' | 'redo' | 'clear' | 'export' | 'exportPdf' | 'setPage';
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
