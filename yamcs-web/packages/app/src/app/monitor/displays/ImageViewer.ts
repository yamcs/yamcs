import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Instance, StorageClient } from '@yamcs/client';
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

  private instance: Instance;
  private storageClient: StorageClient;

  constructor(yamcs: YamcsService, private configService: ConfigService) {
    this.instance = yamcs.getInstance();
    this.storageClient = yamcs.createStorageClient();
  }

  public init(objectName: string) {
    let instance = this.instance.name;
    if (this.configService.getDisplayScope() === 'GLOBAL') {
      instance = '_global';
    }
    this.url = this.storageClient.getObjectURL(instance, 'displays', objectName);
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
