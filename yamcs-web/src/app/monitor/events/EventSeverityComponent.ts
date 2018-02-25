import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

@Component({
  selector: 'app-event-severity',
  templateUrl: './EventSeverityComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventSeverityComponent {

  @Input()
  severity: string;
}
