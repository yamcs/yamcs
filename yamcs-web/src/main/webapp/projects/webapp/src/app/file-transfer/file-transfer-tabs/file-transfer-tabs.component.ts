import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FileTransferService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-file-transfer-tabs',
  templateUrl: './file-transfer-tabs.component.html',
  styleUrl: './file-transfer-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class FileTransferTabsComponent {

  service = input<FileTransferService>();

  constructor(readonly yamcs: YamcsService) {
  }
}
