import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-transmission-constraints-icon',
  templateUrl: './transmission-constraints-icon.component.html',
  styleUrl: './transmission-constraints-icon.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class TransmissionConstraintsIconComponent {

  @Input()
  command: CommandHistoryRecord;
}
