import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AdminPage } from './AdminPage';
import { AdminPageTemplate } from './AdminPageTemplate';
import { AdminRoutingModule, routingComponents } from './AdminRoutingModule';
import { AdminToolbar } from './AdminToolbar';
import { CreateBucketDialog } from './buckets/CreateBucketDialog';
import { RenameObjectDialog } from './buckets/RenameObjectDialog';
import { UploadObjectsDialog } from './buckets/UploadObjectsDialog';
import { UploadProgressDialog } from './buckets/UploadProgressDialog';
import { AddMembersDialog } from './iam/AddMembersDialog';
import { AddRolesDialog } from './iam/AddRolesDialog';
import { ApplicationCredentialsDialog } from './iam/ApplicationCredentialsDialog';
import { ChangeUserPasswordDialog } from './iam/ChangeUserPasswordDialog';
import { UsersTable } from './iam/UsersTable';
import { MessageNamePipe } from './routes/MessageNamePipe';
import { RouteDetail } from './routes/RouteDetail';
import { ServicesTable } from './services/ServicesTable';
import { ServiceState } from './services/ServiceState';

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
    pipes,
    AddMembersDialog,
    AddRolesDialog,
    AdminPage,
    AdminPageTemplate,
    AdminToolbar,
    ApplicationCredentialsDialog,
    ChangeUserPasswordDialog,
    CreateBucketDialog,
    RenameObjectDialog,
    RouteDetail,
    ServiceState,
    ServicesTable,
    UploadObjectsDialog,
    UploadProgressDialog,
    UsersTable,
  ],
})
export class AdminModule {
}
