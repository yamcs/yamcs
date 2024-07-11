import { ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { indentWithTab } from '@codemirror/commands';
import { javascript } from '@codemirror/lang-javascript';
import { EditorState, Extension } from '@codemirror/state';
import { keymap } from '@codemirror/view';
import { ConfigService, MessageService, StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { EditorView, basicSetup } from 'codemirror';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../../../core/services/AuthService';
import { Viewer } from '../Viewer';

@Component({
  standalone: true,
  selector: 'app-script-viewer',
  template: `
    <div #scriptContainer class="script-container"></div>
  `,
  styles: `
    .script-container {
      width: 100%;
      height: 100%;
      display: flex;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ScriptViewerComponent implements Viewer {

  @ViewChild('scriptContainer')
  private scriptContainer: ElementRef<HTMLDivElement>;

  objectName: string;

  private editorView: EditorView;

  private storageClient: StorageClient;
  private bucket: string;

  public hasUnsavedChanges$ = new BehaviorSubject<boolean>(false);

  constructor(
    yamcs: YamcsService,
    configService: ConfigService,
    private messageService: MessageService,
    private authService: AuthService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getDisplayBucket();
  }

  public init(objectName: string) {
    this.objectName = objectName;
    this.storageClient.getObject(this.bucket, objectName)
      .then(response => response.text().then(text => this.initializeEditor(text)))
      .catch(err => this.messageService.showError(err));

    return Promise.resolve();
  }

  private initializeEditor(text: string) {
    const extensions: Extension[] = [
      basicSetup,
      keymap.of([indentWithTab]),
      javascript(),
      EditorView.lineWrapping,
    ];

    if (this.mayManageDisplays()) {
      extensions.push(EditorView.updateListener.of(update => {
        if (update.docChanged) {
          this.hasUnsavedChanges$.next(true);
        }
      }));
    } else {
      extensions.push(EditorState.readOnly.of(true));
    }

    const theme = EditorView.theme({
      '&': {
        width: '100%',
        height: '100%',
        fontSize: '12px',
      },
      '.cm-scroller': {
        overflow: 'auto',
        fontFamily: "'Roboto Mono', monospace",
      },
    }, { dark: false });
    extensions.push(theme);

    const state = EditorState.create({
      doc: text,
      extensions,
    });

    this.editorView = new EditorView({
      state,
      parent: this.scriptContainer.nativeElement,
    });
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
    const text = this.editorView.state.doc.toString();
    const b = new Blob([text], { type: 'text/javascript' });
    return this.storageClient.uploadObject(this.bucket, this.objectName, b).then(() => {
      this.hasUnsavedChanges$.next(false);
    });
  }
}
