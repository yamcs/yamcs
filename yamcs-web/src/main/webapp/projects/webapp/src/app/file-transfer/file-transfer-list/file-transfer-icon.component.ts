import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Transfer, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-file-transfer-icon',
  templateUrl: './file-transfer-icon.component.html',
  styleUrl: './file-transfer-icon.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class FileTransferIconComponent {

  transfer = input.required<Transfer>();
}
