import { Completion, insertCompletionText } from '@codemirror/autocomplete';
import { EditorView } from 'codemirror';

function applyString(view: EditorView, completion: Completion, from: number, to: number) {
  const replacement = completion.label + ' = \"\"';
  const tr = insertCompletionText(view.state, replacement, from, to);
  // Place cursor between quotes
  tr.selection = { anchor: from + replacement.length - 1 };
  view.dispatch(tr);
}

function applyEnum(view: EditorView, completion: Completion, from: number, to: number) {
  view.dispatch(insertCompletionText(view.state, completion.label + ' = ', from, to));
}

function applyNumber(view: EditorView, completion: Completion, from: number, to: number) {
  view.dispatch(insertCompletionText(view.state, completion.label + ' = ', from, to));
}

function applyLogicalOperator(view: EditorView, completion: Completion, from: number, to: number) {
  view.dispatch(insertCompletionText(view.state, completion.label + ' ', from, to));
}

export const EVENT_COMPLETIONS: Completion[] = [
  {
    label: 'message',
    type: 'method',
    info: 'Filter on event message',
    apply: applyString,
  },
  {
    label: 'seqNumber',
    type: 'method',
    info: 'Filter on event sequence number',
    apply: applyNumber,
  },
  {
    label: 'severity',
    type: 'method',
    info: 'Filter on event severity (info, watch, warning, distress, critical, severe)',
    apply: applyEnum,
  },
  {
    label: 'source',
    type: 'method',
    info: 'Filter on event source',
    apply: applyString,
  },
  {
    label: 'type',
    type: 'method',
    info: 'Filter on event type',
    apply: applyString,
  },
  {
    section: 'Exclude events',
    label: '-message',
    type: 'method',
    info: 'Exclude events based on message',
    apply: applyString,
  },
  {
    section: 'Exclude events',
    label: '-seqNumber',
    type: 'method',
    info: 'Exclude events based on sequence number',
    apply: applyNumber,
  },
  {
    section: 'Exclude events',
    label: '-severity',
    type: 'method',
    info: 'Exclude events based on severity (info, watch, warning, distress, critical, severe)',
    apply: applyEnum,
  },
  {
    section: 'Exclude events',
    label: '-source',
    type: 'method',
    info: 'Exclude events based on source',
    apply: applyString,
  },
  {
    section: 'Exclude events',
    label: '-type',
    type: 'method',
    info: 'Exclude events based on type',
    apply: applyString,
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
