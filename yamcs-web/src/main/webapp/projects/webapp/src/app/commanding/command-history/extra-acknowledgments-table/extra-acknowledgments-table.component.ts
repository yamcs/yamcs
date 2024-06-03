import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord, WebappSdkModule } from '@yamcs/webapp-sdk';
import { AcknowledgmentIconComponent } from '../acknowledgment-icon/acknowledgment-icon.component';

@Component({
  standalone: true,
  selector: 'app-extra-acknowledgments-table',
  templateUrl: './extra-acknowledgments-table.component.html',
  styleUrl: './extra-acknowledgments-table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AcknowledgmentIconComponent,
    WebappSdkModule,
  ],
})
export class ExtraAcknowledgmentsTableComponent {

  @Input()
  command: CommandHistoryRecord;

  @Input()
  inline = false;

  @Input()
  showIcons = true;
}
