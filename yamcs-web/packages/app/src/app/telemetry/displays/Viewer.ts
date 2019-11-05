export interface Viewer {

  init(objectName: string): Promise<any>;

  isFullscreenSupported(): boolean;

  isScaleSupported(): boolean;

  hasPendingChanges(): boolean;
}
