import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-yamcs-acknowledgments-table',
  templateUrl: './YamcsAcknowledgmentsTable.html',
  styleUrl: './YamcsAcknowledgmentsTable.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YamcsAcknowledgmentsTable {

  @Input()
  command: CommandHistoryRecord;

  @Input()
  inline = false;

  @Input()
  showIcons = true;
}
