import { LineStyle } from '@fqqb/timeline';
import { ValueType } from './TraceConfig';

export interface State {
  minimum?: number;
  maximum?: number;
  centerZero?: boolean;
  showZeroLine?: boolean;
  showAlarmThresholds?: boolean;
  traces?: TraceState[];
}

export interface TraceState {
  parameter: string;
  color: string;
  lineWidth: number;
  lineStyle: LineStyle;
  fill: boolean;
  valueType: ValueType;
}
