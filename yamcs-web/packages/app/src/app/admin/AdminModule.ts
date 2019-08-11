import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AdminRoutingModule, routingComponents } from './AdminRoutingModule';
import { CreateBucketDialog } from './buckets/CreateBucketDialog';
import { RenameObjectDialog } from './buckets/RenameObjectDialog';
import { UploadObjectsDialog } from './buckets/UploadObjectsDialog';
import { UploadProgressDialog } from './buckets/UploadProgressDialog';
import { EndpointDetail } from './endpoints/EndpointDetail';
import { MessageNamePipe } from './endpoints/MessageNamePipe';
import { UsersTable } from './iam/UsersTable';
import { ServicesTable } from './services/ServicesTable';
import { ServiceState } from './services/ServiceState';

const dialogComponents = [
  CreateBucketDialog,
  RenameObjectDialog,
  UploadObjectsDialog,
  UploadProgressDialog,
];

const pipes = [
  MessageNamePipe,
];

@NgModule({
  imports: [
    SharedModule,
    AdminRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    pipes,
    EndpointDetail,
    UsersTable,
    ServiceState,
    ServicesTable,
  ],
  entryComponents: [
    dialogComponents,
  ],
})
export class AdminModule {
}
