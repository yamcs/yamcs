import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {
  AuthService,
  CodeMirror,
  CodeMirrorService,
  ConfigService,
  MessageService,
  StorageClient,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { Viewer } from '../Viewer';

@Component({
  selector: 'app-script-viewer',
  template: ` <div #scriptContainer class="script-container"></div> `,
  styles: `
    .script-container {
      width: 100%;
      height: 100%;
      display: flex;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ScriptViewerComponent implements Viewer, OnDestroy {
  @ViewChild('scriptContainer')
  private scriptContainer: ElementRef<HTMLDivElement>;

  objectName: string;

  private codeMirror?: CodeMirror;

  private storageClient: StorageClient;
  private bucket: string;

  public hasUnsavedChanges$ = new BehaviorSubject<boolean>(false);

  constructor(
    yamcs: YamcsService,
    configService: ConfigService,
    private messageService: MessageService,
    private authService: AuthService,
    private codeMirrorService: CodeMirrorService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getDisplayBucket();
  }

  public init(objectName: string) {
    this.objectName = objectName;
    this.storageClient
      .getObject(this.bucket, objectName)
      .then((response) =>
        response.text().then((text) => this.initializeEditor(text)),
      )
      .catch((err) => this.messageService.showError(err));

    return Promise.resolve();
  }

  private initializeEditor(text: string) {
    this.codeMirror = this.codeMirrorService.createEditorView({
      parent: this.scriptContainer.nativeElement,
      width: '100%',
      height: '100%',
      readonly: !this.mayManageDisplays(),
      language: 'javascript',
      initialText: text,
      onDirty: (dirty) => this.hasUnsavedChanges$.next(dirty),
    });
  }

  private mayManageDisplays() {
    const user = this.authService.getUser()!;
    return (
      user.hasObjectPrivilege('ManageBucket', this.bucket) ||
      user.hasSystemPrivilege('ManageAnyBucket')
    );
  }

  public hasPendingChanges() {
    return this.hasUnsavedChanges$.value;
  }

  async save() {
    const text = this.codeMirror?.getText() ?? '';
    const b = new Blob([text], { type: 'text/javascript' });
    return this.storageClient
      .uploadObject(this.bucket, this.objectName, b)
      .then(() => {
        this.hasUnsavedChanges$.next(false);
      });
  }

  ngOnDestroy(): void {
    this.codeMirror?.destroy();
  }
}
