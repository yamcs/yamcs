import { ChangeDetectionStrategy, Component, HostBinding, Input } from '@angular/core';
import { CommandHistoryRecord } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-command-arguments',
  templateUrl: './CommandArguments.html',
  styleUrls: ['./CommandArguments.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandArguments {

  @Input({ required: true })
  command: CommandHistoryRecord;

  @Input()
  @HostBinding('class.nomargin')
  nomargin = false;
}
