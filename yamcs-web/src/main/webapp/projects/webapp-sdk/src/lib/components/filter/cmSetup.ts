import { autocompletion, closeBrackets, closeBracketsKeymap, Completion, CompletionContext, completionKeymap } from '@codemirror/autocomplete';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import {
  bracketMatching,
  HighlightStyle,
  indentOnInput,
  syntaxHighlighting
} from '@codemirror/language';
import { lintKeymap } from '@codemirror/lint';
import { highlightSelectionMatches } from '@codemirror/search';
import { EditorState, Extension } from '@codemirror/state';
import {
  crosshairCursor,
  drawSelection,
  dropCursor,
  highlightActiveLine,
  highlightActiveLineGutter,
  highlightSpecialChars,
  keymap,
  lineNumbers,
  placeholder,
  rectangularSelection
} from '@codemirror/view';
import { tags } from '@lezer/highlight';
import { EditorView } from 'codemirror';
import { StyleSpec } from 'style-mod';

const highlightStyle = HighlightStyle.define([
  { tag: tags.compareOperator, color: 'rgb(95, 99, 104)' },
  { tag: tags.lineComment, color: 'rgb(176, 96, 0)' },
  { tag: tags.literal, color: 'rgb(0, 0, 0)' },
  { tag: tags.logicOperator, color: 'rgb(19, 115, 51)' },
  { tag: tags.operator, color: 'rgb(217, 48, 37)' },
  { tag: tags.propertyName, color: 'rgb(118, 39, 187)' },
  { tag: tags.string, color: 'rgb(201, 39, 134)' },
]);

const multilineTheme = EditorView.theme({
  '&': {
    width: '100%',
    height: '100%',
    fontSize: '12px',
    fontWeight: 'normal',
    letterSpacing: 'normal',
  },
  '.cm-content, .cm-gutter': {
    minHeight: '100px',
  },
  '.cm-scroller': {
    overflow: 'auto',
    fontFamily: "'Roboto Mono', monospace",
  },
  '&.cm-focused': {
    outline: 'none',
  },
  ".cm-underline": {
    textDecoration: 'underline 1px red',
    textDecorationStyle: 'wavy',
  },
}, { dark: false });

function createOnelineTheme(paddingLeft: string | undefined) {
  const styles: { [key: string]: StyleSpec; } = {
    '&': {
      width: '100%',
      height: '100%',
      fontSize: '12px',
      fontWeight: 'normal',
      letterSpacing: 'normal',
    },
    '.cm-content': {
      padding: 0,
    },
    '.cm-scroller': {
      overflow: 'auto',
      fontFamily: 'Roboto, sans-serif',
      backgroundColor: '#fff',
      lineHeight: '22px', // 24px - 2px top + bottom border
    },
    '&.cm-focused': {
      outline: 'none',
    },
    '.cm-underline': {
      textDecoration: 'underline 1px red',
      textDecorationStyle: 'wavy',
    },
  };
  if (paddingLeft) {
    styles['.cm-line'] = {
      paddingLeft,
    };
  }
  return EditorView.theme(styles, { dark: false });
}

export interface CodeMirrorConfiguration {
  oneline?: boolean;
  placeholder?: string;
  paddingLeft?: string;
  onEnter?: (view: EditorView) => void;
  completions?: Completion[];
}

export function provideCodeMirrorSetup(options?: CodeMirrorConfiguration): Extension {
  function provideCompletions(context: CompletionContext) {
    const before = context.matchBefore(/\w+/);
    // If completion wasn't explicitly started and there
    // is no word before the cursor, don't open completions.
    if (!context.explicit && !before) {
      return null;
    }
    return {
      from: before ? before.from : context.pos,
      options: options?.completions || [],
      validFor: /^\w*$/
    };
  }

  const extensions: Extension[] = [
    highlightSpecialChars(),
    history(),
    drawSelection(),
    dropCursor(),
    indentOnInput(),
    syntaxHighlighting(highlightStyle),
    bracketMatching(),
    closeBrackets(),
    autocompletion({
      override: [provideCompletions],
    }),
    highlightSelectionMatches(),
  ];

  if (options?.oneline) {

    if (options.onEnter) {
      // Important to have this in the extension array
      // before any other key mappings (higher priority)
      extensions.push(keymap.of([{
        key: 'Enter',
        run: view => {
          options.onEnter!(view);
          return true;
        },
        preventDefault: true
      }]));
    }

    const theme = createOnelineTheme(options?.paddingLeft);
    extensions.push(theme);
  } else {
    extensions.push(...[
      multilineTheme,
      lineNumbers(),
      highlightActiveLineGutter(),
      EditorState.allowMultipleSelections.of(true),
      rectangularSelection(),
      crosshairCursor(),
      highlightActiveLine(),
      EditorView.lineWrapping,
    ]);
  }

  extensions.push(
    keymap.of([
      ...closeBracketsKeymap,
      ...defaultKeymap,
      ...historyKeymap,
      ...completionKeymap,
      ...lintKeymap,
    ]));

  if (options?.placeholder) {
    extensions.push(placeholder(options.placeholder));
  }

  return extensions;
}
