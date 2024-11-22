import { AfterViewInit, Component, ElementRef, Inject, OnDestroy, signal, viewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { markdown } from '@codemirror/lang-markdown';
import { Extension } from '@codemirror/state';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { basicSetup, EditorView } from 'codemirror';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';

@Component({
  standalone: true,
  selector: 'app-edit-text-entry-dialog',
  templateUrl: './edit-text-entry-dialog.component.html',
  styleUrl: './edit-text-entry-dialog.component.css',
  imports: [
    MarkdownComponent,
    WebappSdkModule,
  ],
})
export class EditTextEntryDialogComponent implements AfterViewInit, OnDestroy {

  textContainerRef = viewChild.required<ElementRef<HTMLDivElement>>('textContainer');
  editorView?: EditorView;

  text = signal<string>('');

  constructor(
    private dialogRef: MatDialogRef<EditTextEntryDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
  ) {
  }

  ngAfterViewInit(): void {
    this.text.set(this.data.entry?.text || '');

    const extensions: Extension[] = [
      basicSetup,
      EditorView.lineWrapping,
      markdown(),
      EditorView.updateListener.of(update => {
        if (update.docChanged) {
          const newValue = update.state.doc.toString();
          this.text.set(newValue);
        }
      }),
    ];

    const theme = EditorView.theme({
      '&': {
        height: '100%',
        fontSize: '12px',
      },
      '.cm-scroller': {
        overflow: 'auto',
        fontFamily: "'Roboto Mono', monospace",
      },
      '&.cm-focused': {
        outline: 'none',
      },
    }, { dark: false });
    extensions.push(theme);

    this.editorView = new EditorView({
      doc: this.text(),
      extensions,
      parent: this.textContainerRef().nativeElement,
    });
    this.editorView.focus();
  }

  save() {
    const text = this.editorView?.state.doc.toString() || '';
    this.dialogRef.close(text);
  }

  gotoMarkdownDocs() {
    window.open('https://www.markdownguide.org/basic-syntax/', '_blank', 'noreferrer');
  }

  ngOnDestroy(): void {
    this.editorView?.destroy();
  }
}
