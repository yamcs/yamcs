import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { DyLegendData, TimestampTrackerData } from '../parameter-plot/dygraphs';

@Component({
  standalone: true,
  selector: 'app-timestamp-tracker',
  templateUrl: './timestamp-tracker.component.html',
  styleUrl: './timestamp-tracker.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class TimestampTrackerComponent {

  @Input()
  legendData?: DyLegendData; // Use this to hide timestamp when mouse leaves canvas

  @Input()
  timestampData: TimestampTrackerData;
}
