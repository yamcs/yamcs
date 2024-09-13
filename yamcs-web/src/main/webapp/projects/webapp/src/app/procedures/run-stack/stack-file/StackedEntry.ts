import { CheckStep, Command, CommandHistoryRecord, CommandStep, ParameterValue, Step } from '@yamcs/webapp-sdk';

export type StackedEntry = StackedCheckEntry | StackedCommandEntry;

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
}

export interface NamedParameterValue {
  parameter: string;
  pval: ParameterValue | null;
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
