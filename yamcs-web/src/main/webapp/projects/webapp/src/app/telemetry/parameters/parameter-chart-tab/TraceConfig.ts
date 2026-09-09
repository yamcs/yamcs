import { LineStyle } from '@fqqb/timeline';
import { EnumValue, Parameter } from '@yamcs/webapp-sdk';

export type ValueType = 'raw' | 'engineering';

export interface TraceConfig {
  parameter: Parameter;
  color: string;
  lineWidth: number;
  lineStyle: LineStyle;
  fill: boolean;
  valueType: ValueType;

  /**
   * Enum value table for an enum-typed engineering trace, used to render
   * ordinal↔label on the axis, tooltip and legend. Absent for numeric traces.
   */
  enumValues?: EnumValue[];
}
