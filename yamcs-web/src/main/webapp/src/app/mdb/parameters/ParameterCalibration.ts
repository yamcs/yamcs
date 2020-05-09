import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Parameter } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-parameter-calibration',
  templateUrl: './ParameterCalibration.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterCalibration {

  @Input()
  parameter: Parameter;

  constructor(readonly yamcs: YamcsService) {
  }
}
