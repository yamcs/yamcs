import { ChangeDetectionStrategy, Component, ElementRef, Input, ViewChild } from '@angular/core';
import { javascript } from '@codemirror/lang-javascript';
import { python } from '@codemirror/lang-python';
import { EditorState, Extension } from '@codemirror/state';
import { Algorithm, YamcsService } from '@yamcs/webapp-sdk';
import { EditorView, basicSetup } from 'codemirror';

@Component({
  selector: 'app-algorithm-detail',
  templateUrl: './AlgorithmDetail.html',
  styleUrl: './AlgorithmDetail.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmDetail {

  @Input()
  algorithm: Algorithm;

  constructor(readonly yamcs: YamcsService) {
  }

  @ViewChild('text')
  set textContainer(textContainer: ElementRef<HTMLDivElement>) {
    const extensions: Extension[] = [
      basicSetup,
      EditorState.readOnly.of(true),
      EditorView.theme({
        '&': { height: '300px', fontSize: '12px' },
        '.cm-scroller': {
          overflow: 'auto',
          fontFamily: "'Roboto Mono', monospace",
        },
      }, { dark: false }),
    ];

    switch (this.algorithm.language.toLowerCase()) {
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
