import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, ViewChild } from '@angular/core';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/mode/python';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { Algorithm } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-algorithm-detail',
  templateUrl: './AlgorithmDetail.html',
  styleUrls: ['./AlgorithmDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmDetail implements AfterViewInit {

  @ViewChild('text', { static: true })
  textContainer: ElementRef;

  @Input()
  algorithm: Algorithm;

  @Input()
  readonly = false;

  private editor: ace.Editor;

  constructor(readonly yamcs: YamcsService) {
  }

  ngAfterViewInit() {
    this.editor = ace.edit(this.textContainer.nativeElement);
    this.editor.setReadOnly(this.readonly);

    switch (this.algorithm.language.toLowerCase()) {
      case 'javascript':
        this.editor.getSession().setMode('ace/mode/javascript');
        break;
      case 'python':
        this.editor.getSession().setMode('ace/mode/python');
        break;
      default:
        console.warn(`Unexpected language ${this.algorithm.language}`);
    }

    this.editor.setTheme('ace/theme/eclipse');
  }
}
