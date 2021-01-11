import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { FileTransferRoutingModule, routingComponents } from './FileTransferRoutingModule';
import { FileTransferTable } from './FileTransferTable';
import { UploadFileDialog } from './UploadFileDialog';

@NgModule({
  imports: [
    SharedModule,
    FileTransferRoutingModule,
  ],
  declarations: [
    routingComponents,
    FileTransferTable,
    UploadFileDialog,
  ],
})
export class FileTransferModule {
}
