import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AdminHomeRoutingModule, routingComponents } from './AdminHomeRoutingModule';
import { AdminPage } from './AdminPage';
import { AdminPageTemplate } from './AdminPageTemplate';
import { AdminToolbar } from './AdminToolbar';
import { CreateBucketDialog } from './buckets/CreateBucketDialog';
import { RenameObjectDialog } from './buckets/RenameObjectDialog';
import { UploadObjectsDialog } from './buckets/UploadObjectsDialog';
import { ServicesTable } from './services/ServicesTable';
import { ServiceState } from './services/ServiceState';

const dialogComponents = [
  CreateBucketDialog,
  RenameObjectDialog,
  UploadObjectsDialog,
];

@NgModule({
  imports: [
    SharedModule,
    AdminHomeRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    AdminPage,
    AdminPageTemplate,
    AdminToolbar,
    ServiceState,
    ServicesTable,
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class AdminModule {
}
