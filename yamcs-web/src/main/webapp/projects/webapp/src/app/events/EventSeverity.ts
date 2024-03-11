import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-event-severity',
  templateUrl: './EventSeverity.html',
  styleUrl: './EventSeverity.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventSeverity {

  @Input()
  severity: string;

  @Input()
  grayscale = false;
}
