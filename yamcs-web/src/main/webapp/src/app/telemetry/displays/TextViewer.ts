import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { StorageClient } from '../../client';
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

  private storageClient: StorageClient;
  private bucket: string;

  constructor(
    yamcs: YamcsService,
    private changeDetector: ChangeDetectorRef,
    configService: ConfigService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getDisplayBucket();

  }

  public init(objectName: string) {
    this.storageClient.getObject(this.bucket, objectName).then(response => {
      response.text().then(text => {
        this.text = text;
        this.changeDetector.detectChanges();
      });
    });
    return Promise.resolve();
  }

  public hasPendingChanges() {
    return false;
  }
}
