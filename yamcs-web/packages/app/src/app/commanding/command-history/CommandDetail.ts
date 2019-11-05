import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Instance } from '@yamcs/client';
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

  instance: Instance;

  constructor(yamcs: YamcsService) {
    this.instance = yamcs.getInstance();
  }
}
