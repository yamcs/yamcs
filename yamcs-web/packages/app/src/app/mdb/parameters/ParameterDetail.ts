import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { Parameter, ParameterValue } from '@yamcs/client';

@Component({
  selector: 'app-parameter-detail',
  templateUrl: './ParameterDetail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterDetail {

  @Input()
  instance: string;

  @Input()
  parameter: Parameter;

  @Input()
  parameterValue: ParameterValue;
}
