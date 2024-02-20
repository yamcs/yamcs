import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommandHistoryRecord, utils } from '@yamcs/webapp-sdk';

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
