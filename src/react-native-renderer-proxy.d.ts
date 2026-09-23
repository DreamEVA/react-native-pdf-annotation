declare module 'react-native/Libraries/ReactNative/RendererProxy' {
  export function dispatchCommand(
    handle: object,
    command: string,
    args: Array<string | number>,
  ): void;
}
