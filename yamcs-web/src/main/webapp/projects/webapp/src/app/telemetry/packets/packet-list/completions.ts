import { Completion, insertCompletionText } from '@codemirror/autocomplete';
import { EditorView } from 'codemirror';

function applyString(
  view: EditorView,
  completion: Completion,
  from: number,
  to: number,
) {
  const replacement = completion.label + ' = \"\"';
  const tr = insertCompletionText(view.state, replacement, from, to);
  // Place cursor between quotes
  tr.selection = { anchor: from + replacement.length - 1 };
  view.dispatch(tr);
}

function applyNumber(
  view: EditorView,
  completion: Completion,
  from: number,
  to: number,
) {
  view.dispatch(
    insertCompletionText(view.state, completion.label + ' = ', from, to),
  );
}

function applyLogicalOperator(
  view: EditorView,
  completion: Completion,
  from: number,
  to: number,
) {
  view.dispatch(
    insertCompletionText(view.state, completion.label + ' ', from, to),
  );
}

export const PACKET_COMPLETIONS: Completion[] = [
  {
    label: 'name',
    type: 'method',
    info: 'Filter by packet name',
    apply: applyString,
  },
  {
    label: 'binary',
    type: 'method',
    info: 'Filter by hex packet binary',
    apply: applyString,
  },
  {
    label: 'link',
    type: 'method',
    info: 'Filter by link',
    apply: applyString,
  },
  {
    label: 'seqNumber',
    type: 'method',
    info: 'Filter by packet sequence number',
    apply: applyNumber,
  },
  {
    label: 'size',
    type: 'method',
    info: 'Filter by packet size',
    apply: applyNumber,
  },
  {
    section: 'Exclude packets',
    label: '-name',
    type: 'method',
    info: 'Exclude packets based on name',
    apply: applyString,
  },
  {
    section: 'Exclude packets',
    label: '-binary',
    type: 'method',
    info: 'Exclude packets based on hex packet binary',
    apply: applyString,
  },
  {
    section: 'Exclude packets',
    label: '-link',
    type: 'method',
    info: 'Exclude packets based on link',
    apply: applyString,
  },
  {
    section: 'Exclude packets',
    label: '-seqNumber',
    type: 'method',
    info: 'Exclude packets based on sequence number',
    apply: applyNumber,
  },
  {
    section: 'Exclude packets',
    label: '-size',
    type: 'method',
    info: 'Exclude packets based on packet size',
    apply: applyNumber,
  },
  {
    section: 'Logical operators',
    label: 'AND',
    type: 'constant',
    apply: applyLogicalOperator,
  },
  {
    section: 'Logical operators',
    label: 'OR',
    type: 'constant',
    apply: applyLogicalOperator,
  },
  {
    section: 'Logical operators',
    label: 'NOT',
    type: 'constant',
    apply: applyLogicalOperator,
  },
];
