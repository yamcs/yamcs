import { Component, Input } from '@angular/core';
import { CommandHistoryRecord, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-transmission-constraints-icon',
  templateUrl: './transmission-constraints-icon.component.html',
  styleUrl: './transmission-constraints-icon.component.css',
  imports: [WebappSdkModule],
})
export class TransmissionConstraintsIconComponent {
  @Input()
  command: CommandHistoryRecord;
}
