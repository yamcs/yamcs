export const ARG_OPSNAME = 'Opsname';
export const ARG_PATHNAME = 'Pathname';
export const ARG_SID = 'SID';

export class DataBinding {

  dynamicProperty: string;
  type: string;

  opsname: string;
  pathname: string;
  sid: string;

  usingRaw: boolean;

  // For computations
  expression: string;
  args: { [key: string]: string } = {};
  DEFAULT: string;
}
