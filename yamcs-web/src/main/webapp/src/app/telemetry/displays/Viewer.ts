export interface Viewer {

  init(objectName: string): Promise<any>;

  hasPendingChanges(): boolean;
}
