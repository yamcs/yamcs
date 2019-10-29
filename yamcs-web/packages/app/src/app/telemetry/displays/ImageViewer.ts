import { ChangeDetectionStrategy, Component } from '@angular/core';
import { StorageClient } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-image-viewer',
  templateUrl: './ImageViewer.html',
  styles: [`
    .checkerboard {
      position: absolute;
      width: 100%;
      height: 100%;
    }
    .wrapper {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%,-50%);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImageViewer implements Viewer {

  url: string;

  private storageClient: StorageClient;

  constructor(yamcs: YamcsService) {
    this.storageClient = yamcs.createStorageClient();
  }

  public init(objectName: string) {
    this.url = this.storageClient.getObjectURL('_global', 'displays', objectName);
    return Promise.resolve();
  }

  public isFullscreenSupported() {
    return false;
  }

  public isScaleSupported() {
    return false;
  }

  public hasPendingChanges() {
    return false;
  }
}
