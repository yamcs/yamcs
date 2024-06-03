import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-event-severity',
  templateUrl: './event-severity.component.html',
  styleUrl: './event-severity.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class EventSeverityComponent {

  @Input()
  severity: string;

  @Input()
  grayscale = false;
}
