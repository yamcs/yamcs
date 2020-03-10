import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Parameter } from '../../client';

@Component({
  selector: 'app-parameter-calibration',
  templateUrl: './ParameterCalibration.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterCalibration {

  @Input()
  instance: string;

  @Input()
  parameter: Parameter;
}
