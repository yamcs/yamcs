import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import {
  Algorithm,
  CodeMirror,
  CodeMirrorLanguage,
  CodeMirrorService,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';

@Component({
  selector: 'app-algorithm-detail',
  templateUrl: './algorithm-detail.component.html',
  styleUrl: './algorithm-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MarkdownComponent, WebappSdkModule],
})
export class AlgorithmDetailComponent implements OnDestroy {
  @Input()
  algorithm: Algorithm;

  private codeMirror?: CodeMirror;

  constructor(
    readonly yamcs: YamcsService,
    private codeMirrorService: CodeMirrorService,
  ) {}

  @ViewChild('text')
  set textContainer(textContainer: ElementRef<HTMLDivElement>) {
    let language: CodeMirrorLanguage = 'plain';
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
    }

    this.codeMirror = this.codeMirrorService.createEditorView({
      parent: textContainer.nativeElement,
      height: '300px',
      readonly: true,
      language,
      initialText: this.algorithm.text,
    });
  }

  ngOnDestroy(): void {
    this.codeMirror?.destroy();
  }
}
