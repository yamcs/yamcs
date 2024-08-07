import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Transfer, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-file-transfer-icon',
  templateUrl: './file-transfer-icon.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class FileTransferIconComponent {

  transfer = input.required<Transfer>();
}
