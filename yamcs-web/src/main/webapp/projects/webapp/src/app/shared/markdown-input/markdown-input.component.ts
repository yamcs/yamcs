import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, forwardRef, input, OnDestroy, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap } from "@codemirror/autocomplete";
import { defaultKeymap, history, historyKeymap } from "@codemirror/commands";
import { markdown } from '@codemirror/lang-markdown';
import {
  bracketMatching,
  defaultHighlightStyle,
  indentOnInput,
  syntaxHighlighting
} from "@codemirror/language";
import { lintKeymap } from "@codemirror/lint";
import { highlightSelectionMatches } from "@codemirror/search";
import { EditorState, Extension } from '@codemirror/state';
import {
  crosshairCursor,
  drawSelection,
  dropCursor,
  highlightSpecialChars,
  keymap
} from "@codemirror/view";
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { EditorView } from 'codemirror';
import { MarkdownComponent } from '../markdown/markdown.component';

@Component({
  standalone: true,
  selector: 'app-markdown-input',
  templateUrl: './markdown-input.component.html',
  styleUrl: './markdown-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => AppMarkdownInput),
    multi: true,
  }],
  imports: [
    MarkdownComponent,
    WebappSdkModule,
  ],
})
export class AppMarkdownInput implements ControlValueAccessor, AfterViewInit, OnDestroy {

  height = input('100px');

  private editorContainerRef = viewChild.required<ElementRef<HTMLDivElement>>('editorContainer');
  private editorView: EditorView | null = null;

  private onChange = (_: string | null) => { };

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
    this.editorView?.dispatch({
      changes: {
        from: 0,
        to: this.editorView.state.doc.length,
        insert: this.initialDocString,
      },
    });
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  private initializeEditor(targetEl: HTMLDivElement) {
    const extensions: Extension[] = [
      highlightSpecialChars(),
      history(),
      drawSelection(),
      dropCursor(),
      EditorState.allowMultipleSelections.of(true),
      indentOnInput(),
      syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
      bracketMatching(),
      closeBrackets(),
      autocompletion(),
      crosshairCursor(),
      highlightSelectionMatches(),
      keymap.of([
        ...closeBracketsKeymap,
        ...defaultKeymap,
        ...historyKeymap,
        ...completionKeymap,
        ...lintKeymap,
      ]),
      EditorView.lineWrapping,
      markdown(),
      EditorView.updateListener.of(update => {
        if (update.docChanged) {
          const newValue = update.state.doc.toString();
          this.text.set(newValue);
          this.onChange(newValue);
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
      doc: this.initialDocString,
      extensions,
      parent: targetEl,
    });
  }

  gotoMarkdownDocs() {
    window.open('https://www.markdownguide.org/basic-syntax/', '_blank', 'noreferrer');
  }

  ngOnDestroy(): void {
    this.editorView?.destroy();
    this.editorView = null;
  }
}
