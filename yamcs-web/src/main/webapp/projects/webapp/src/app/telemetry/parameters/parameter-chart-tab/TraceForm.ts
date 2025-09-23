import { FormControl } from '@angular/forms';
import { LineStyle } from '@fqqb/timeline';
import { ValueType } from './TraceConfig';

export interface TraceForm {
  /**
   * Assigned internal ID
   */
  traceId: FormControl<string>;

  /**
   * Requested parameter name. Should be a qualified name,
   * but really the user can type anything.
   */
  parameter: FormControl<string>;

  lineColor: FormControl<string>;
  lineWidth: FormControl<number>;
  lineStyle: FormControl<LineStyle>;
  fill: FormControl<boolean>;
  valueType: FormControl<ValueType>;
}
