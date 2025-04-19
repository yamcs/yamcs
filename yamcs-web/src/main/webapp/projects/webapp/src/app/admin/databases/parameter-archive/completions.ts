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

function applyEnum(
  view: EditorView,
  completion: Completion,
  from: number,
  to: number,
) {
  view.dispatch(
    insertCompletionText(view.state, completion.label + ' = ', from, to),
  );
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

export const PID_COMPLETIONS: Completion[] = [
  {
    label: 'pid',
    type: 'method',
    info: 'Filter by PID',
    apply: applyNumber,
  },
  {
    label: 'parameter',
    type: 'method',
    info: 'Filter by parameter name',
    apply: applyString,
  },
  {
    label: 'rawType',
    type: 'method',
    info: 'Filter by raw type',
    apply: applyEnum,
  },
  {
    label: 'engType',
    type: 'method',
    info: 'Filter by engineering type',
    apply: applyEnum,
  },
  {
    label: 'gid',
    type: 'method',
    info: 'Filter by GID',
    apply: applyNumber,
  },
  {
    section: 'Exclude PIDs',
    label: '-pid',
    type: 'method',
    info: 'Exclude based on PID',
    apply: applyNumber,
  },
  {
    section: 'Exclude PIDs',
    label: '-parameter',
    type: 'method',
    info: 'Exclude based on parameter name',
    apply: applyString,
  },
  {
    section: 'Exclude PIDs',
    label: '-rawType',
    type: 'method',
    info: 'Exclude based on raw type',
    apply: applyEnum,
  },
  {
    section: 'Exclude PIDs',
    label: '-engType',
    type: 'method',
    info: 'Exclude based on engineering type',
    apply: applyEnum,
  },
  {
    section: 'Exclude PIDs',
    label: '-gid',
    type: 'method',
    info: 'Exclude based on GID',
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
