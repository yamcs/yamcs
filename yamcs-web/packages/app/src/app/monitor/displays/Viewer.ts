export interface Viewer {

  loadPath(path: string): void;

  isFullscreenSupported(): boolean;

  hasPendingChanges(): boolean;
}
