import { ParameterBinding } from './ParameterBinding';

export class Parameter {

  type: string; // 'ExternalDataSource' | 'Computation';

  name: string;

  bindings: ParameterBinding[] = [];

  // Computation
  expression: string;
  args: { [key: string]: string } = {};
}
