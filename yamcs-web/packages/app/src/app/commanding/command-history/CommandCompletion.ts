import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord } from './CommandHistoryRecord';

@Component({
  selector: 'app-command-completion',
  templateUrl: './CommandCompletion.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandCompletion {

  @Input()
  item: CommandHistoryRecord;
}
