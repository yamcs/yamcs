export interface Viewer {

  init(objectName: string): void;

  isFullscreenSupported(): boolean;

  hasPendingChanges(): boolean;
}
