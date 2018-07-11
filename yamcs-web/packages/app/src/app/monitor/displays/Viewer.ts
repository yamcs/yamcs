export interface Viewer {

  init(objectName: string): Promise<any>;

  isFullscreenSupported(): boolean;

  hasPendingChanges(): boolean;
}
