import { DataBinding } from './DataBinding';

export class ParameterBinding extends DataBinding {

  static readonly TYPE = 'PARAMETER';

  opsName?: string;
  pathName?: string;
  sid?: string;

  constructor() {
    super(ParameterBinding.TYPE);
  }

  toString() {
    return `[${this.dynamicProperty}] ${this.opsName}`;
  }
}
