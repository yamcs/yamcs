import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord, WebappSdkModule, utils } from '@yamcs/webapp-sdk';
import { HexComponent } from '../../../shared/hex/hex.component';
import { CommandArgumentsComponent } from '../command-arguments/command-arguments.component';
import { ExtraAcknowledgmentsTableComponent } from '../extra-acknowledgments-table/extra-acknowledgments-table.component';
import { CascadingPrefixPipe } from '../shared/cascading-prefix.pipe';
import { YamcsAcknowledgmentsTableComponent } from '../yamcs-acknowledgments-table/yamcs-acknowledgments-table.component';

@Component({
  standalone: true,
  selector: 'app-command-detail2',
  templateUrl: './command-detail.component.html',
  styleUrl: './command-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CascadingPrefixPipe,
    CommandArgumentsComponent,
    ExtraAcknowledgmentsTableComponent,
    HexComponent,
    WebappSdkModule,
    YamcsAcknowledgmentsTableComponent,
  ],
})
export class CommandDetailComponent {

  @Input()
  command: CommandHistoryRecord;

  @Input()
  showIcons = true;

  constructor(private clipboard: Clipboard) {
  }

  copyHex(base64: string) {
    const hex = utils.convertBase64ToHex(base64);
    this.clipboard.copy(hex);
  }

  copyBinary(base64: string) {
    const raw = window.atob(base64);
    this.clipboard.copy(raw);
  }
}
