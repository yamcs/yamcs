
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { DyLegendData } from '../dygraphs';

@Component({
  standalone: true,
  selector: 'app-parameter-legend',
  templateUrl: './parameter-legend.component.html',
  styleUrl: './parameter-legend.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIcon
],
})
export class ParameterLegendComponent {

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
