import { LineStyle } from '@fqqb/timeline';
import { Parameter } from '@yamcs/webapp-sdk';

export type ValueType = 'raw' | 'engineering';

export interface TraceConfig {
  parameter: Parameter;
  color: string;
  lineWidth: number;
  lineStyle: LineStyle;
  fill: boolean;
  valueType: ValueType;
}
