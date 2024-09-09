import { ChangeDetectionStrategy, Component, ElementRef, Input, ViewChild } from '@angular/core';
import { java } from '@codemirror/lang-java';
import { javascript } from '@codemirror/lang-javascript';
import { python } from '@codemirror/lang-python';
import { EditorState, Extension } from '@codemirror/state';
import { Algorithm, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { EditorView, basicSetup } from 'codemirror';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';

@Component({
  standalone: true,
  selector: 'app-algorithm-detail',
  templateUrl: './algorithm-detail.component.html',
  styleUrl: './algorithm-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MarkdownComponent,
    WebappSdkModule,
  ],
})
export class AlgorithmDetailComponent {

  @Input()
  algorithm: Algorithm;

  constructor(readonly yamcs: YamcsService) {
  }

  @ViewChild('text')
  set textContainer(textContainer: ElementRef<HTMLDivElement>) {
    const extensions: Extension[] = [
      basicSetup,
      EditorState.readOnly.of(true),
      EditorView.lineWrapping,
      EditorView.theme({
        '&': { height: '300px', fontSize: '12px' },
        '.cm-scroller': {
          overflow: 'auto',
          fontFamily: "'Roboto Mono', monospace",
        },
      }, { dark: false }),
    ];

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

    new EditorView({
      state,
      parent: textContainer.nativeElement,
    });
  }
}
