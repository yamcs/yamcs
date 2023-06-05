import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ParameterType } from '@yamcs/webapp-sdk';
import { YamcsService } from '../../core/services/YamcsService';

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
