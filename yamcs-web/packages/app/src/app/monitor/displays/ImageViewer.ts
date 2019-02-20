import { ChangeDetectionStrategy, Component } from '@angular/core';
import { StorageClient } from '@yamcs/client';
import { ConfigService } from '../../core/services/ConfigService';
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

  constructor(yamcs: YamcsService, private configService: ConfigService) {
    this.storageClient = yamcs.createStorageClient();
  }

  public init(objectName: string) {
    const bucketInstance = this.configService.getDisplayBucketInstance();
    this.url = this.storageClient.getObjectURL(bucketInstance, 'displays', objectName);
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
