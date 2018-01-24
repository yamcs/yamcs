import { DataBinding } from './DataBinding';

export const ARG_OPSNAME = 'Opsname';
export const ARG_PATHNAME = 'Pathname';
export const ARG_SID = 'SID';

export class ParameterBinding extends DataBinding {

  static readonly TYPE = 'PARAMETER';

  opsName: string;
  pathName: string;
  sid: string;

  constructor() {
    super(ParameterBinding.TYPE);
  }

  toString() {
    return `[${this.dynamicProperty}] ${this.opsName}`;
  }
}
