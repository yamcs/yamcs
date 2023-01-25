import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { FileTransferRoutingModule, routingComponents } from './FileTransferRoutingModule';
import { FileTransferTable } from './FileTransferTable';
import { RemoteFileSelector } from './RemoteFileSelector';
import { TransferFileDialog } from './TransferFileDialog';

@NgModule({
  imports: [
    SharedModule,
    FileTransferRoutingModule,
  ],
  declarations: [
    routingComponents,
    FileTransferTable,
    RemoteFileSelector,
    TransferFileDialog,
  ],
})
export class FileTransferModule {
}
