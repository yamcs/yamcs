import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ParameterType, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-parameter-calibration',
  templateUrl: './ParameterCalibration.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterCalibration {

  @Input()
  ptype: ParameterType;

  constructor(readonly yamcs: YamcsService) {
  }
}
