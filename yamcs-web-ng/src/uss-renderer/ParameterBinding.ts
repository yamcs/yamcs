import { DataSourceBinding } from './DataSourceBinding';

export class ParameterBinding extends DataSourceBinding {

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
