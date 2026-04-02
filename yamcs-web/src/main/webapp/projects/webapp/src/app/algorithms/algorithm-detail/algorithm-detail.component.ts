import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {
  Algorithm,
  AlgorithmOverrides,
  AlgorithmStatus,
  AuthService,
  CodeMirror,
  CodeMirrorLanguage,
  CodeMirrorService,
  MessageService,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { MarkdownComponent } from '../../shared/markdown/markdown.component';
import { AlgorithmStatusComponent } from '../algorithm-status/algorithm-status.component';

@Component({
  selector: 'app-algorithm-detail',
  templateUrl: './algorithm-detail.component.html',
  styleUrl: './algorithm-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AlgorithmStatusComponent, MarkdownComponent, WebappSdkModule],
})
export class AlgorithmDetailComponent implements AfterViewInit, OnDestroy {
  @ViewChild('text')
  textContainer: ElementRef<HTMLDivElement>;

  @Input({ required: true })
  algorithm: Algorithm;

  @Input()
  status: AlgorithmStatus | null;

  overrides$ = new BehaviorSubject<AlgorithmOverrides | null>(null);

  codeMirror?: CodeMirror;
  dirty$ = new BehaviorSubject<boolean>(false);

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private authService: AuthService,
    private codeMirrorService: CodeMirrorService,
  ) {}

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
    let language: CodeMirrorLanguage;
    switch (this.algorithm.language.toLowerCase()) {
      case 'java-expression':
        language = 'java';
        break;
      case 'javascript':
        language = 'javascript';
        break;
      case 'python':
        language = 'python';
        break;
      default:
        console.warn(`Unexpected language ${this.algorithm.language}`);
        language = 'plain';
    }

    this.codeMirror = this.codeMirrorService.createEditorView({
      parent: this.textContainer.nativeElement,
      height: '300px',
      readonly: this.isChangeMissionDatabaseEnabled(),
      language,
      initialText: this.algorithm.text,
      onDirty: (dirty) => this.dirty$.next(dirty),
    });
  }

  private refreshOverrides() {
    const qualifiedName = this.algorithm.qualifiedName;
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    this.yamcs.yamcsClient
      .getAlgorithmOverrides(instance, processor, qualifiedName)
      .then((overrides) => {
        this.overrides$.next(overrides);
        if (overrides.textOverride) {
          this.codeMirror?.setText(overrides.textOverride.text);
        } else {
          this.codeMirror?.setText(this.algorithm.text);
        }
      })
      .catch((err) => this.messageService.showError(err));
  }

  saveTextChanges() {
    const text = this.codeMirror?.getText() ?? '';
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    this.yamcs.yamcsClient
      .updateAlgorithmText(
        instance,
        processor,
        this.algorithm.qualifiedName,
        text,
      )
      .then(() => this.refreshOverrides())
      .catch((err) => this.messageService.showError(err));
  }

  revertText() {
    const instance = this.yamcs.instance!;
    const processor = this.yamcs.processor!;
    this.yamcs.yamcsClient
      .revertAlgorithmText(instance, processor, this.algorithm.qualifiedName)
      .then(() => this.refreshOverrides())
      .catch((err) => this.messageService.showError(err));
  }

  ngOnDestroy(): void {
    this.codeMirror?.destroy();
  }
}
