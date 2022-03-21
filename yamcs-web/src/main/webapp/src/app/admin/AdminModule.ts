import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AdminRoutingModule, routingComponents } from './AdminRoutingModule';
import { StreamDataComponent } from './databases/database/stream/StreamDataComponent';
import { RecordComponent } from './databases/database/table/RecordComponent';
import { ShowEnumDialog } from './databases/database/table/ShowEnumDialog';
import { ColumnValuePipe } from './databases/pipes/ColumnValuePipe';
import { Shell } from './databases/shell/Shell';
import { UserAgentPipe } from './http-traffic/UserAgentPipe';
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
  UserAgentPipe,
  ColumnValuePipe,
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
    RecordComponent,
    ReplicationState,
    RouteDetail,
    ServiceState,
    ServicesTable,
    Shell,
    ShowEnumDialog,
    StreamDataComponent,
    ThreadsTable,
    TraceElement,
    UsersTable,
  ],
})
export class AdminModule {
}
