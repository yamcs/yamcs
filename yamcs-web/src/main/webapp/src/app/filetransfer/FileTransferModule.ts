import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { FileTransferRoutingModule, routingComponents } from './FileTransferRoutingModule';
import { FileTransferTable } from './FileTransferTable';
import { DownloadFileDialog } from './DownloadFileDialog';
import { RemoteFileSelector } from './RemoteFileSelector';

@NgModule({
  imports: [
    SharedModule,
    FileTransferRoutingModule,
  ],
  declarations: [
    routingComponents,
    FileTransferTable,
    RemoteFileSelector,
    DownloadFileDialog,
  ],
})
export class FileTransferModule {
}
