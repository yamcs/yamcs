import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { StorageClient } from '../../client';
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

  private storageClient: StorageClient;

  constructor(yamcs: YamcsService, private changeDetector: ChangeDetectorRef) {
    this.storageClient = yamcs.createStorageClient();
  }

  public init(objectName: string) {
    this.storageClient.getObject('_global', 'displays', objectName).then(response => {
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
