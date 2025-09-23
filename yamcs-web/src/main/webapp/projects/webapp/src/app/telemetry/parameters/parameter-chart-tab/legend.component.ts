import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Legend } from './Legend';
import { LegendItem } from './LegendItem';

export interface SelectItemEvent {
  target: HTMLDivElement;
  item: LegendItem;
}

@Component({
  selector: 'app-legend',
  templateUrl: './legend.component.html',
  styleUrl: './legend.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIcon, MatTooltipModule],
})
export class LegendComponent {
  @Input()
  leftMargin = 63;

  @Input()
  data: Legend;

  @Input()
  backgroundColor: string;

  @Input()
  borderColor: string;

  @Input()
  error: string | null;
}
