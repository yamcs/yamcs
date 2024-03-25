import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-event-severity',
  templateUrl: './event-severity.component.html',
  styleUrl: './event-severity.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class EventSeverityComponent {

  @Input()
  severity: string;

  @Input()
  grayscale = false;
}
