import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, ViewChild } from '@angular/core';
import { indentWithTab } from '@codemirror/commands';
import { java } from '@codemirror/lang-java';
import { javascript } from '@codemirror/lang-javascript';
import { python } from '@codemirror/lang-python';
import { EditorState, Extension } from '@codemirror/state';
import { keymap } from '@codemirror/view';
import { Algorithm, AlgorithmOverrides, AlgorithmStatus, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { EditorView, basicSetup } from 'codemirror';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { MarkdownComponent } from '../../shared/markdown/markdown.component';
import { AlgorithmStatusComponent } from '../algorithm-status/algorithm-status.component';

@Component({
  standalone: true,
  selector: 'app-algorithm-detail',
  templateUrl: './algorithm-detail.component.html',
  styleUrl: './algorithm-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlgorithmStatusComponent,
    MarkdownComponent,
    WebappSdkModule,
  ],
})
export class AlgorithmDetailComponent implements AfterViewInit {

  @ViewChild('text')
  textContainer: ElementRef<HTMLDivElement>;

  @Input({ required: true })
  algorithm: Algorithm;

  @Input()
  status: AlgorithmStatus | null;

  overrides$ = new BehaviorSubject<AlgorithmOverrides | null>(null);

  private editorView: EditorView;
  dirty$ = new BehaviorSubject<boolean>(false);

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private authService: AuthService,
  ) { }

  ngAfterViewInit() {
    if (this.algorithm.text) {
      this.initializeEditor();
      this.refreshOverrides();
    }
  }

  isChangeMissionDatabaseEnabled() {
    const user = this.authService.getUser()!;
    return user.hasSystemPrivilege('ChangeMissionDatabase');
  }

  private initializeEditor() {
    const extensions: Extension[] = [
      basicSetup,
      keymap.of([indentWithTab]),
      EditorView.lineWrapping,
    ];

    if (this.isChangeMissionDatabaseEnabled()) {
      extensions.push(EditorView.updateListener.of(update => {
        if (update.docChanged) {
          this.dirty$.next(true);
        }
      }));
    } else {
      extensions.push(EditorState.readOnly.of(true));
    }

    const theme = EditorView.theme({
      '&': { height: '300px', fontSize: '12px' },
      '.cm-scroller': {
        overflow: 'auto',
        fontFamily: "'Roboto Mono', monospace",
      },
    }, { dark: false });
    extensions.push(theme);

    switch (this.algorithm.language.toLowerCase()) {
      case 'java-expression':
        extensions.push(java());
        break;
      case 'javascript':
        extensions.push(javascript());
        break;
      case 'python':
        extensions.push(python());
        break;
      default:
        console.warn(`Unexpected language ${this.algorithm.language}`);
    }

    const state = EditorState.create({
      doc: this.algorithm.text,
      extensions,
    });

    this.editorView = new EditorView({
      state,
      parent: this.textContainer.nativeElement,
    });
  }

  private refreshOverrides() {
    const qualifiedName = this.algorithm.qualifiedName;
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    this.yamcs.yamcsClient.getAlgorithmOverrides(instance, processor, qualifiedName).then(overrides => {
      this.overrides$.next(overrides);
      if (overrides.textOverride) {
        this.updateEditorValue(overrides.textOverride.text);
      } else {
        this.updateEditorValue(this.algorithm.text);
      }
    }).catch(err => this.messageService.showError(err));
  }

  private updateEditorValue(text: string) {
    this.editorView.dispatch({
      changes: {
        from: 0,
        to: this.editorView.state.doc.length,
        insert: text,
      },
    });

    this.dirty$.next(false);
  }

  saveTextChanges() {
    const text = this.editorView.state.doc.toString();
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    this.yamcs.yamcsClient.updateAlgorithmText(instance, processor, this.algorithm.qualifiedName, text)
      .then(() => this.refreshOverrides())
      .catch(err => this.messageService.showError(err));
  }

  revertText() {
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    this.yamcs.yamcsClient.revertAlgorithmText(instance, processor, this.algorithm.qualifiedName)
      .then(() => this.refreshOverrides())
      .catch(err => this.messageService.showError(err));
  }
}
