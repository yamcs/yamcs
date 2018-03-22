import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { Parameter } from '@yamcs/client';

@Component({
  selector: 'app-parameter-calibration',
  templateUrl: './ParameterCalibration.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterCalibration {

  @Input()
  parameter: Parameter;
}
