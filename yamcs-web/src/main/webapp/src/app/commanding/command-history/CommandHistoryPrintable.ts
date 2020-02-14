import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Printable } from '../../shared/print/Printable';
import { CommandHistoryRecord } from './CommandHistoryRecord';

@Component({
  selector: 'app-command-history-printable',
  templateUrl: './CommandHistoryPrintable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandHistoryPrintable implements Printable {

  @Input()
  pageTitle: string;

  @Input()
  data: CommandHistoryRecord[];
}
