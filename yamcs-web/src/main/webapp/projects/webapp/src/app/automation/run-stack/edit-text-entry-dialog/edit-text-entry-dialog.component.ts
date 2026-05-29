import {
  AfterViewInit,
  Component,
  ElementRef,
  Inject,
  OnDestroy,
  signal,
  viewChild,
} from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  CodeMirror,
  CodeMirrorService,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';

@Component({
  selector: 'app-edit-text-entry-dialog',
  templateUrl: './edit-text-entry-dialog.component.html',
  styleUrl: './edit-text-entry-dialog.component.css',
  imports: [MarkdownComponent, WebappSdkModule],
})
export class EditTextEntryDialogComponent implements AfterViewInit, OnDestroy {
  textContainerRef =
    viewChild.required<ElementRef<HTMLDivElement>>('textContainer');

  codeMirror?: CodeMirror;

  text = signal<string>('');

  constructor(
    private dialogRef: MatDialogRef<EditTextEntryDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: any,
    private codeMirrorService: CodeMirrorService,
  ) {}

  ngAfterViewInit(): void {
    this.text.set(this.data.entry?.text || '');

    this.codeMirror = this.codeMirrorService.createEditorView({
      parent: this.textContainerRef().nativeElement,
      height: '100%',
      language: 'markdown',
      readonly: false,
      initialText: this.text(),
      focus: true,
      onChange: (text) => this.text.set(text),
    });
  }

  save() {
    const text = this.codeMirror?.getText() ?? '';
    this.dialogRef.close(text);
  }

  gotoMarkdownDocs() {
    window.open(
      'https://www.markdownguide.org/basic-syntax/',
      '_blank',
      'noreferrer',
    );
  }

  ngOnDestroy(): void {
    this.codeMirror?.destroy();
  }
}
