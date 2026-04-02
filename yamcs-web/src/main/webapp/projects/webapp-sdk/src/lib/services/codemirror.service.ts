import { effect, inject, Injectable } from '@angular/core';
import {
  autocompletion,
  closeBrackets,
  closeBracketsKeymap,
  completionKeymap,
} from '@codemirror/autocomplete';
import {
  defaultKeymap,
  history,
  historyKeymap,
  indentWithTab,
} from '@codemirror/commands';
import { java } from '@codemirror/lang-java';
import { javascript } from '@codemirror/lang-javascript';
import { markdown } from '@codemirror/lang-markdown';
import { python } from '@codemirror/lang-python';
import {
  bracketMatching,
  defaultHighlightStyle,
  foldGutter,
  foldKeymap,
  indentOnInput,
  syntaxHighlighting,
} from '@codemirror/language';
import { lintKeymap } from '@codemirror/lint';
import { highlightSelectionMatches } from '@codemirror/search';
import { Compartment, EditorState, Extension } from '@codemirror/state';
import { oneDark } from '@codemirror/theme-one-dark';
import {
  crosshairCursor,
  drawSelection,
  dropCursor,
  highlightActiveLine,
  highlightActiveLineGutter,
  highlightSpecialChars,
  keymap,
  lineNumbers,
} from '@codemirror/view';
import { EditorView } from 'codemirror';
import { AppearanceService } from './appearance.service';

export type CodeMirrorLanguage =
  | 'java'
  | 'javascript'
  | 'markdown'
  | 'plain'
  | 'python';

export interface CodeMirrorOptions {
  parent: Element;
  width?: string;
  height?: string;
  readonly: boolean;
  language: CodeMirrorLanguage;
  initialText?: string;
  /**
   * Request to focus the editor view
   */
  focus?: boolean;
  /**
   * Whether to show the left gutter. Defaults to true
   */
  gutter?: boolean;
  onDirty?: (dirty: boolean) => void;
  onChange?: (text: string) => void;
}

export class CodeMirror {
  private themeCompartment = new Compartment();
  private editorView: EditorView;

  constructor(
    private codeMirrorService: CodeMirrorService,
    private opts: CodeMirrorOptions,
    dark: boolean,
  ) {
    const gutter = opts.gutter ?? true;

    // Adapted from CodeMirror's "basicSetup".
    const extensions: Extension[] = [
      gutter ? lineNumbers() : [],
      gutter ? highlightActiveLineGutter() : [],
      highlightSpecialChars(),
      history(),
      gutter ? foldGutter() : [],
      drawSelection(),
      dropCursor(),
      EditorState.allowMultipleSelections.of(true),
      indentOnInput(),
      syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
      bracketMatching(),
      closeBrackets(),
      autocompletion(),
      // rectangularSelection(),
      crosshairCursor(),
      gutter ? highlightActiveLine() : [],
      highlightSelectionMatches(),
      keymap.of([
        ...closeBracketsKeymap,
        ...defaultKeymap,
        // ...searchKeymap,
        ...historyKeymap,
        ...(gutter ? foldKeymap : []),
        ...completionKeymap,
        ...lintKeymap,
        indentWithTab,
      ]),
      EditorView.lineWrapping,
    ];

    if (opts.readonly) {
      extensions.push(EditorState.readOnly.of(true));
    } else {
      extensions.push(
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            opts.onDirty?.(true);
            opts.onChange?.(update.state.doc.toString());
          }
        }),
      );
    }

    const baseTheme = EditorView.theme({
      '&': {
        width: this.opts.width ?? null,
        height: this.opts.height ?? null,
        backgroundColor: 'var(--y-background-color)',
        fontSize: '12px',
      },
      '.cm-scroller': {
        overflow: 'auto',
        fontFamily: "'Roboto Mono', monospace",
        backgroundColor: 'var(--y-background-color)',
      },
      '&.cm-focused': {
        outline: 'none',
      },
    });

    extensions.push(baseTheme);
    if (dark) {
      extensions.push(this.themeCompartment.of([oneDark]));
    } else {
      extensions.push(this.themeCompartment.of([]));
    }

    switch (opts.language) {
      case 'java':
        extensions.push(java());
        break;
      case 'javascript':
        extensions.push(javascript());
        break;
      case 'markdown':
        extensions.push(markdown());
        break;
      case 'python':
        extensions.push(python());
        break;
      default:
        console.warn(`Unexpected language ${opts.language}`);
    }

    const state = EditorState.create({
      doc: opts.initialText ?? '',
      extensions,
    });

    this.editorView = new EditorView({
      state,
      parent: opts.parent,
    });
    if (opts.focus ?? false) {
      this.editorView.focus();
    }
  }

  getText() {
    return this.editorView.state.doc.toString();
  }

  setText(text: string) {
    this.editorView.dispatch({
      changes: {
        from: 0,
        to: this.editorView.state.doc.length,
        insert: text,
      },
    });

    this.opts.onDirty?.(false);
  }

  setDark(dark: boolean) {
    const newTheme = dark ? [oneDark] : [];
    this.editorView.dispatch({
      effects: this.themeCompartment.reconfigure(newTheme),
    });
  }

  destroy() {
    this.codeMirrorService.removeInstance(this);
    this.editorView.destroy();
  }
}

@Injectable({
  providedIn: 'root',
})
export class CodeMirrorService {
  private appearanceService = inject(AppearanceService);
  private instances = new Set<CodeMirror>();

  constructor() {
    effect(() => {
      const isDark = this.appearanceService.dark();
      for (const instance of this.instances) {
        instance.setDark(isDark);
      }
    });
  }

  createEditorView(opts: CodeMirrorOptions): CodeMirror {
    const dark = this.appearanceService.dark();
    const codeMirror = new CodeMirror(this, opts, dark);
    this.instances.add(codeMirror);
    return codeMirror;
  }

  /** @hidden */
  removeInstance(codeMirror: CodeMirror) {
    this.instances.delete(codeMirror);
  }
}
