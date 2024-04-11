import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord, WebappSdkModule } from '@yamcs/webapp-sdk';
import { AcknowledgmentIconComponent } from '../acknowledgment-icon/acknowledgment-icon.component';
import { TransmissionConstraintsIconComponent } from '../transmission-constraints-icon/transmission-constraints-icon.component';

@Component({
  standalone: true,
  selector: 'app-yamcs-acknowledgments-table',
  templateUrl: './yamcs-acknowledgments-table.component.html',
  styleUrl: './yamcs-acknowledgments-table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AcknowledgmentIconComponent,
    WebappSdkModule,
    TransmissionConstraintsIconComponent,
  ],
})
export class YamcsAcknowledgmentsTableComponent {

  @Input()
  command: CommandHistoryRecord;

  @Input()
  inline = false;

  @Input()
  showIcons = true;
}
