import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-command-arguments',
  templateUrl: './command-arguments.component.html',
  styleUrl: './command-arguments.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.nomargin]': 'nomargin',
  },
  imports: [WebappSdkModule],
})
export class CommandArgumentsComponent {
  @Input({ required: true })
  command: CommandHistoryRecord;

  @Input()
  nomargin = false;
}
