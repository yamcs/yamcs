import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { DyLegendData, TimestampTrackerData } from './dygraphs';

@Component({
  selector: 'app-timestamp-tracker',
  templateUrl: './TimestampTracker.html',
  styleUrls: ['./TimestampTracker.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TimestampTracker {

  @Input()
  legendData: DyLegendData; // Use this to hide timestamp when mouse leaves canvas

  @Input()
  timestampData: TimestampTrackerData;
}
