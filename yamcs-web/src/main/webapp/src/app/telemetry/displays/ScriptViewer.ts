import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, ViewChild } from '@angular/core';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { StorageClient } from '../../client';
import { ConfigService } from '../../core/services/ConfigService';
import { YamcsService } from '../../core/services/YamcsService';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-script-viewer',
  template: `
    <div #scriptContainer class="script-container"></div>
  `,
  styles: [`
    .script-container {
      height: 100%;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScriptViewer implements Viewer {

  @ViewChild('scriptContainer', { static: true })
  private scriptContainer: ElementRef;

  private editor: ace.Editor;

  private storageClient: StorageClient;
  private bucket: string;

  constructor(
    yamcs: YamcsService,
    private changeDetector: ChangeDetectorRef,
    configService: ConfigService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getConfig().displayBucket;
  }

  public init(objectName: string) {
    this.storageClient.getObject('_global', this.bucket, objectName).then(response => {
      response.text().then(text => {
        this.scriptContainer.nativeElement.innerHTML = text;
        this.editor = ace.edit(this.scriptContainer.nativeElement);
        this.editor.setReadOnly(true);
        this.editor.getSession().setMode('ace/mode/javascript');
        this.changeDetector.detectChanges();
      });
    });

    this.editor.setTheme('ace/theme/eclipse');

    return Promise.resolve();
  }

  public hasPendingChanges() {
    return false;
  }
}
