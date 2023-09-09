import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-transmission-constraints-icon',
  templateUrl: './TransmissionConstraintsIcon.html',
  styleUrls: ['./TransmissionConstraintsIcon.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransmissionConstraintsIcon {

  @Input()
  command: CommandHistoryRecord;
}
