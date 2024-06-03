import { ChangeDetectionStrategy, Component, HostBinding, Input } from '@angular/core';
import { CommandHistoryRecord, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-command-arguments',
  templateUrl: './command-arguments.component.html',
  styleUrl: './command-arguments.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class CommandArgumentsComponent {

  @Input({ required: true })
  command: CommandHistoryRecord;

  @Input()
  @HostBinding('class.nomargin')
  nomargin = false;
}
