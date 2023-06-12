import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-parameter-series',
  template: '',
})
export class ParameterSeries {

  @Input()
  parameter: string;

  @Input()
  grid = false;

  @Input()
  axis = true;

  @Input()
  axisLineWidth = 1;

  @Input()
  alarmRanges: 'line' | 'fill' | 'none' = 'line';

  @Input()
  color = '#1b61b9';

  @Input()
  label: string;

  @Input()
  strokeWidth = 2;
}
