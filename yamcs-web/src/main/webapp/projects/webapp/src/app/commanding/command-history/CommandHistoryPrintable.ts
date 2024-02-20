import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord, Printable } from '@yamcs/webapp-sdk';

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
