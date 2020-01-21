import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CfdpRoutingModule, routingComponents } from './CfdpRoutingModule';
import { FileTransferTable } from './FileTransferTable';
import { UploadFileDialog } from './UploadFileDialog';

@NgModule({
  imports: [
    SharedModule,
    CfdpRoutingModule,
  ],
  declarations: [
    routingComponents,
    FileTransferTable,
    UploadFileDialog,
  ],
})
export class CfdpModule {
}
