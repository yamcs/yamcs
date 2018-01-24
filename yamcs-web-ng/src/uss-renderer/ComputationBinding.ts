import { DataBinding } from './DataBinding';

export class ComputationBinding extends DataBinding {

  static readonly TYPE = 'COMPUTATION';

  name: string;
  expression: string;
  args: { [key: string]: string } = {};

  constructor() {
    super(ComputationBinding.TYPE);
  }

  toString() {
    return `[${this.dynamicProperty}] ${this.name}`;
  }
}
