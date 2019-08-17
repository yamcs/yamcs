import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AdminRoutingModule, routingComponents } from './AdminRoutingModule';
import { CreateBucketDialog } from './buckets/CreateBucketDialog';
import { RenameObjectDialog } from './buckets/RenameObjectDialog';
import { UploadObjectsDialog } from './buckets/UploadObjectsDialog';
import { UploadProgressDialog } from './buckets/UploadProgressDialog';
import { AddMembersDialog } from './iam/AddMembersDialog';
import { ApplicationCredentialsDialog } from './iam/ApplicationCredentialsDialog';
import { UsersTable } from './iam/UsersTable';
import { MessageNamePipe } from './routes/MessageNamePipe';
import { RouteDetail } from './routes/RouteDetail';
import { ServicesTable } from './services/ServicesTable';
import { ServiceState } from './services/ServiceState';

const dialogComponents = [
  AddMembersDialog,
  ApplicationCredentialsDialog,
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
    RouteDetail,
    ServiceState,
    ServicesTable,
    UsersTable,
  ],
  entryComponents: [
    dialogComponents,
  ],
})
export class AdminModule {
}
