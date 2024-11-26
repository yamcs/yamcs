import { signal, WritableSignal } from '@angular/core';
import { CheckStep, Command, CommandHistoryRecord, CommandStep, ParameterValue, Step, TextStep, VerifyStep } from '@yamcs/webapp-sdk';
import { renderValue } from '../../../commanding/command-sender/arguments/argument/argument.component';

export type StackedEntry = StackedCheckEntry
  | StackedCommandEntry
  | StackedTextEntry
  | StackedVerifyEntry;

abstract class AbstractStackedEntry<T extends Step> {
  model: T;

  executionNumber?: number;
  executing = false;
  err?: string;

  constructor(model: T) {
    this.model = model;
  }

  get comment() { return this.model.comment; }

  clearOutputs(): void {
    this.executionNumber = undefined;
    this.err = undefined;
  }

  hasOutputs(): boolean {
    return !!this.err;
  }

  abstract copy(): AbstractStackedEntry<T>;
}

export class StackedCommandEntry extends AbstractStackedEntry<CommandStep> {

  type: 'command' = 'command';
  command?: Command;
  id?: string;
  record?: CommandHistoryRecord;

  constructor(model: CommandStep) {
    super(model);
  }

  get name() { return this.model.name; }
  get namespace() { return this.model.namespace; }
  get args() { return this.model.args; }
  get extra() { return this.model.extra; }
  get stream() { return this.model.stream; }
  get advancement() { return this.model.advancement; }

  override clearOutputs(): void {
    super.clearOutputs();
    this.id = undefined;
    this.record = undefined;
  }

  override hasOutputs(): boolean {
    return super.hasOutputs() || !!this.record;
  }

  override copy(): StackedCommandEntry {
    const copiedEntry = new StackedCommandEntry({
      type: 'command',
      name: this.name,
      namespace: this.namespace,
      args: { ...this.args },
      comment: this.comment,
      stream: this.stream,
    });
    copiedEntry.command = this.command;
    if (this.extra) {
      copiedEntry.model.extra = { ...this.extra };
    }
    if (this.advancement) {
      copiedEntry.model.advancement = { ...this.advancement };
    }
    return copiedEntry;
  }

  override toString(): string {
    let res = this.name;
    if (this.args) {
      res += ' [';
      let first = true;
      for (const k in this.args) {
        if (first) {
          first = false;
        } else {
          res += ', ';
        }
        res += `${k}=${this.args[k]}`;
      }
      res += ']';
    }
    return res;
  };
}

export interface NamedParameterValue {
  parameter: string;
  pval: ParameterValue | null;
  status?: WritableSignal<'ok' | 'nok' | 'pending' | 'cancelled' | null>;
}

export class StackedVerifyEntry extends AbstractStackedEntry<VerifyStep> {

  type: 'verify' = 'verify';
  pvals?: NamedParameterValue[];

  constructor(model: VerifyStep) {
    super(model);
  }

  get condition() { return this.model.condition; }
  get delay() { return this.model.delay; }
  get timeout() { return this.model.timeout; }

  override clearOutputs(): void {
    super.clearOutputs();
    this.pvals = undefined;
  }

  override hasOutputs(): boolean {
    return super.hasOutputs() || !!this.pvals;
  }

  override copy() {
    return new StackedVerifyEntry({
      type: 'verify',
      condition: this.condition.map(c => ({ ...c })),
      comment: this.comment,
    });
  }

  override toString(): string {
    let res = '';
    let first = true;
    for (let comparison of this.condition || []) {
      if (first) {
        first = false;
      } else {
        res += ' AND ';
      }
      res += comparison.parameter;
      switch (comparison.operator) {
        case 'eq':
          res += ' = ';
          break;
        case 'neq':
          res += ' != ';
          break;
        case 'lt':
          res += ' < ';
          break;
        case 'lte':
          res += ' <= ';
          break;
        case 'gt':
          res += ' > ';
          break;
        case 'gte':
          res += ' >= ';
          break;
      }
      res += comparison.value;
    }
    return res;
  };

  test(pvals: { [key: string]: ParameterValue; }): boolean {
    const matchedPvals: NamedParameterValue[] = [];
    for (const comparison of this.condition) {
      const allPvals = pvals;
      matchedPvals.push({
        parameter: comparison.parameter,
        pval: allPvals[comparison.parameter] || null,
        status: signal(null),
      });
    }
    this.pvals = matchedPvals;

    let allOK = true;
    for (let i = 0; i < this.condition.length; i++) {
      const matchedPval = matchedPvals[i];
      if (matchedPval === null || !matchedPval.pval?.engValue) {
        allOK = false;
        continue;
      } else if (!matchedPval.pval?.engValue) {
        allOK = false;
        matchedPval.status!.set('pending');
        continue;
      } else {
        const actual = renderValue(matchedPval.pval.engValue);
        const comparand = String(this.condition[i].value);
        switch (this.condition[i].operator) {
          case 'eq':
            if (actual === comparand) {
              matchedPval.status!.set('ok');
            } else {
              allOK = false;
              matchedPval.status!.set('pending');
            }
            break;
          case 'neq':
            if (actual !== comparand) {
              matchedPval.status!.set('ok');
            } else {
              allOK = false;
              matchedPval.status!.set('pending');
            }
            break;
          case 'lt':
            if (isNaN(actual) || isNaN(comparand as any)) {
              allOK = false;
              matchedPval.status!.set('pending');
            } else if (Number(actual) < Number(comparand)) {
              matchedPval.status!.set('ok');
            } else {
              allOK = false;
              matchedPval.status!.set('pending');
            }
            break;
          case 'lte':
            if (isNaN(actual) || isNaN(comparand as any)) {
              allOK = false;
              matchedPval.status!.set('pending');
            } else if (Number(actual) <= Number(comparand)) {
              matchedPval.status!.set('ok');
            } else {
              allOK = false;
              matchedPval.status!.set('pending');
            }
            break;
          case 'gt':
            if (isNaN(actual) || isNaN(comparand as any)) {
              allOK = false;
              matchedPval.status!.set('pending');
            } else if (Number(actual) > Number(comparand)) {
              matchedPval.status!.set('ok');
            } else {
              allOK = false;
              matchedPval.status!.set('pending');
            }
            break;
          case 'gte':
            if (isNaN(actual) || isNaN(comparand as any)) {
              allOK = false;
              matchedPval.status!.set('pending');
            } else if (Number(actual) >= Number(comparand)) {
              matchedPval.status!.set('ok');
            } else {
              allOK = false;
              matchedPval.status!.set('pending');
            }
            break;
        }
      }
    }

    return allOK;
  }
}

export class StackedCheckEntry extends AbstractStackedEntry<CheckStep> {

  type: 'check' = 'check';
  pvals?: NamedParameterValue[];

  constructor(model: CheckStep) {
    super(model);
  }

  get parameters() { return this.model.parameters; }

  override clearOutputs(): void {
    super.clearOutputs();
    this.pvals = undefined;
  }

  override hasOutputs(): boolean {
    return super.hasOutputs() || !!this.pvals;
  }

  override copy() {
    return new StackedCheckEntry({
      type: 'check',
      parameters: this.parameters.map(p => ({ ...p })),
      comment: this.comment,
    });
  }
}

export class StackedTextEntry extends AbstractStackedEntry<TextStep> {

  type: 'text' = 'text';

  renderedText = signal<string>('');

  constructor(model: TextStep) {
    super(model);
  }

  get text() { return this.model.text; }

  override copy() {
    return new StackedTextEntry({
      type: 'text',
      text: this.text,
    });
  }
}
