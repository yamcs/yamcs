import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';
import { CommandHistoryRecord } from './CommandHistoryRecord';

@Component({
  selector: 'app-command-detail2',
  templateUrl: './CommandDetail.html',
  styleUrls: ['./CommandDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandDetail {

  @Input()
  command: CommandHistoryRecord;

  @Input()
  showIcons = true;

  instance: string;

  constructor(yamcs: YamcsService) {
    this.instance = yamcs.getInstance();
  }
}
