import { Component, Input } from '@angular/core';
import { Parameter } from '@yamcs/client';

@Component({
  selector: 'app-parameter-series',
  template: '',
})
export class ParameterSeries {

  @Input()
  parameter: Parameter;

  @Input()
  grid = false;

  @Input()
  axis = true;

  @Input()
  axisLineWidth = 1;
}
