import { ParameterBinding } from './ParameterBinding';

export class Parameter {

  type: string; // 'ExternalDataSource' | 'Computation';

  name: string;

  bindings: ParameterBinding[] = [];

  // TODO Should these not be in another (value) object?
  generationTime: any;
  acquisitionStatus: any;
  monitoringResult: any;
  rawValue: any;
  engValue: any;

  // Computation
  expression: string;
  args: { [key: string]: string } = {};
}
