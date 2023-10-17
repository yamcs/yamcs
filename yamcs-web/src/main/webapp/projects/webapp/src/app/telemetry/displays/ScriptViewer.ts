import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, ViewChild } from '@angular/core';
import { ConfigService, StorageClient, YamcsService } from '@yamcs/webapp-sdk';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
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
  private scriptContainer: ElementRef<HTMLDivElement>;

  objectName: string;

  private editor: ace.Editor;

  private storageClient: StorageClient;
  private bucket: string;

  public hasUnsavedChanges$ = new BehaviorSubject<boolean>(false);

  constructor(
    yamcs: YamcsService,
    private changeDetector: ChangeDetectorRef,
    configService: ConfigService,
    private authService: AuthService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getDisplayBucket();
  }

  public init(objectName: string) {
    this.objectName = objectName;
    this.storageClient.getObject(this.bucket, objectName).then(response => {
      response.text().then(text => {
        this.editor = ace.edit(this.scriptContainer.nativeElement);
        this.editor.$blockScrolling = Infinity; // Required to suppress a warning
        this.editor.getSession().setMode('ace/mode/javascript');
        this.changeDetector.detectChanges();

        this.editor.setTheme('ace/theme/eclipse');
        this.editor.setValue(text, -1);

        if (this.mayManageDisplays()) {
          this.editor.addEventListener('change', () => {
            this.hasUnsavedChanges$.next(true);
          });
        } else {
          this.editor.setReadOnly(true);
        }
      });
    });

    return Promise.resolve();
  }

  private mayManageDisplays() {
    const user = this.authService.getUser()!;
    return user.hasObjectPrivilege('ManageBucket', this.bucket)
      || user.hasSystemPrivilege('ManageAnyBucket');
  }

  public hasPendingChanges() {
    return this.hasUnsavedChanges$.value;
  }

  async save() {
    const text = this.editor.getSession().getValue();
    const b = new Blob([text], { type: 'text/javascript' });
    return this.storageClient.uploadObject(this.bucket, this.objectName, b).then(() => {
      this.hasUnsavedChanges$.next(false);
    });
  }
}
