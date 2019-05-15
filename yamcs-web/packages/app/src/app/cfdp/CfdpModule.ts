import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CfdpRoutingModule, routingComponents } from './CfdpRoutingModule';
import { FileTransferTable } from './FileTransferTable';
import { ObjectSelector } from './ObjectSelector';
import { UploadFileDialog } from './UploadFileDialog';

const dialogComponents = [
  UploadFileDialog,
];

@NgModule({
  imports: [
    SharedModule,
    CfdpRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    FileTransferTable,
    ObjectSelector,
  ],
  entryComponents: [
    dialogComponents,
  ],
})
export class CfdpModule {
}
