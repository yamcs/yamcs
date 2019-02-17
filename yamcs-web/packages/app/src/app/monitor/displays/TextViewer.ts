import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { Instance, StorageClient } from '@yamcs/client';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-text-viewer',
  template: `
    <pre *ngIf="text">{{ text }}</pre>
  `,
  styles: [`
    pre {
      margin: 1em;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TextViewer implements Viewer {

  text: string;

  private instance: Instance;
  private storageClient: StorageClient;

  constructor(yamcs: YamcsService, private configService: ConfigService, private changeDetector: ChangeDetectorRef) {
    this.instance = yamcs.getInstance();
    this.storageClient = yamcs.createStorageClient();
  }

  public init(objectName: string) {
    let instance = this.instance.name;
    if (this.configService.getDisplayScope() === 'GLOBAL') {
      instance = '_global';
    }
    this.storageClient.getObject(instance, 'displays', objectName).then(response => {
      response.text().then(text => {
        this.text = text;
        this.changeDetector.detectChanges();
      });
    });
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
