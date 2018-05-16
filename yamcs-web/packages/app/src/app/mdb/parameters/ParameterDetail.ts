import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Instance, Parameter, ParameterValue } from '@yamcs/client';

@Component({
  selector: 'app-parameter-detail',
  templateUrl: './ParameterDetail.html',
  styleUrls: ['./ParameterDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterDetail {

  @Input()
  instance: Instance;

  @Input()
  parameter: Parameter;

  @Input()
  currentValue: ParameterValue;
}
