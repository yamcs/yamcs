import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord, Printable, WebappSdkModule } from '@yamcs/webapp-sdk';
import { CommandDetailComponent } from '../command-detail/command-detail.component';

@Component({
  standalone: true,
  selector: 'app-command-history-printable',
  templateUrl: './command-history-printable.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommandDetailComponent,
    WebappSdkModule,
  ],
})
export class CommandHistoryPrintableComponent implements Printable {

  @Input()
  pageTitle: string;

  @Input()
  data: CommandHistoryRecord[];
}
