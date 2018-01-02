export class DataBinding {

  dynamicProperty: 'X' | 'Y' | 'FILL_COLOR' | 'VALUE';
  type: string;

  parameterName: string;
  parameterNamespace: string;

  usingRaw: boolean;

  // For computations
  expression: string;
  args: any[];
}
