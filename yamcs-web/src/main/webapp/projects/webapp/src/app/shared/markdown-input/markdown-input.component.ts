import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  forwardRef,
  inject,
  OnDestroy,
  signal,
  viewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import {
  CodeMirror,
  CodeMirrorService,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';
import { MarkdownComponent } from '../markdown/markdown.component';

@Component({
  selector: 'app-markdown-input',
  templateUrl: './markdown-input.component.html',
  styleUrl: './markdown-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AppMarkdownInput),
      multi: true,
    },
  ],
  imports: [MarkdownComponent, WebappSdkModule],
})
export class AppMarkdownInput
  implements ControlValueAccessor, AfterViewInit, OnDestroy
{
  private codeMirrorService = inject(CodeMirrorService);

  private editorContainerRef =
    viewChild.required<ElementRef<HTMLDivElement>>('editorContainer');
  private codeMirror?: CodeMirror;

  private onChange = (_: string | null) => {};

  // Internal value, for when a value is received before CM init
  private initialDocString: string | undefined;

  text = signal<string>('');

  ngAfterViewInit(): void {
    const targetEl = this.editorContainerRef().nativeElement;
    this.initializeEditor(targetEl);
  }

  writeValue(value: any): void {
    this.initialDocString = value || undefined;
    this.text.set(this.initialDocString || '');
    this.codeMirror?.setText(this.initialDocString ?? '');
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {}

  private initializeEditor(targetEl: HTMLDivElement) {
    this.codeMirror = this.codeMirrorService.createEditorView({
      parent: targetEl,
      height: '100%',
      readonly: false,
      language: 'markdown',
      initialText: this.initialDocString,
      gutter: false,
      onChange: (text: string) => {
        this.text.set(text);
        this.onChange(text);
      },
    });
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
