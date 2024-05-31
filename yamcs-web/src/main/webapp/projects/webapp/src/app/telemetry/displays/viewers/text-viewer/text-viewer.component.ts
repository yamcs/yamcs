import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { ConfigService, StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { Viewer } from '../Viewer';

@Component({
  standalone: true,
  selector: 'app-text-viewer',
  template: `
    @if (text) {
      <pre>{{ text }}</pre>
    }
    `,
  styles: `
    pre {
      margin: 1em;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class TextViewerComponent implements Viewer {

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
