import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AdminRoutingModule, routingComponents } from './AdminRoutingModule';
import { CreateBucketDialog } from './buckets/CreateBucketDialog';
import { RenameObjectDialog } from './buckets/RenameObjectDialog';
import { UploadObjectsDialog } from './buckets/UploadObjectsDialog';
import { UploadProgressDialog } from './buckets/UploadProgressDialog';
import { AddMembersDialog } from './iam/AddMembersDialog';
import { AddRolesDialog } from './iam/AddRolesDialog';
import { ApplicationCredentialsDialog } from './iam/ApplicationCredentialsDialog';
import { ChangeUserPasswordDialog } from './iam/ChangeUserPasswordDialog';
import { UsersTable } from './iam/UsersTable';
import { ReplicationState } from './replication/ReplicationState';
import { MessageNamePipe } from './routes/MessageNamePipe';
import { RouteDetail } from './routes/RouteDetail';
import { ServicesTable } from './services/ServicesTable';
import { ServiceState } from './services/ServiceState';
import { AdminSharedModule } from './shared/AdminSharedModule';
import { ThreadsTable } from './threads/ThreadsTable';
import { TraceElement } from './threads/TraceElement';

const pipes = [
  MessageNamePipe,
];

@NgModule({
  imports: [
    SharedModule,
    AdminSharedModule,
    AdminRoutingModule,
  ],
  declarations: [
    routingComponents,
    pipes,
    AddMembersDialog,
    AddRolesDialog,
    ApplicationCredentialsDialog,
    ChangeUserPasswordDialog,
    CreateBucketDialog,
    RenameObjectDialog,
    ReplicationState,
    RouteDetail,
    ServiceState,
    ServicesTable,
    ThreadsTable,
    TraceElement,
    UploadObjectsDialog,
    UploadProgressDialog,
    UsersTable,
  ],
})
export class AdminModule {
}
