import { Parameter, ParameterValue } from '@yamcs/client';

export interface ParameterWithValue extends Parameter {
  pval?: ParameterValue;
}
