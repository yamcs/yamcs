import { DataBinding } from './DataBinding';

export class Parameter {

  namespace: string;
  name: string;
  type: string;
  bindings: DataBinding[] = [];

  // Computation
  expression: any;
  args: any;
}
