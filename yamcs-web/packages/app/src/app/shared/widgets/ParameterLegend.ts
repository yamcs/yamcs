import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { DyLegendData } from './dygraphs';

@Component({
  selector: 'app-parameter-legend',
  templateUrl: './ParameterLegend.html',
  styleUrls: ['./ParameterLegend.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterLegend {

  @Input()
  data: DyLegendData;

  @Input()
  backgroundColor: string;

  @Input()
  borderColor: string;

  @Input()
  closable = false;

  @Output()
  select = new EventEmitter<string>();

  @Output()
  close = new EventEmitter<string>();
}
