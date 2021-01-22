import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { FileTransferRoutingModule, routingComponents } from './FileTransferRoutingModule';
import { FileTransferTable } from './FileTransferTable';
import { UploadFileDialog } from './UploadFileDialog';
import { DownloadFileDialog } from './DownloadFileDialog';

@NgModule({
  imports: [
    SharedModule,
    FileTransferRoutingModule,
  ],
  declarations: [
    routingComponents,
    FileTransferTable,
    UploadFileDialog,
    DownloadFileDialog,
  ],
})
export class FileTransferModule {
}
