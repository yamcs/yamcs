import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AdminHomeRoutingModule, routingComponents } from './AdminHomeRoutingModule';
import { AdminPage } from './AdminPage';
import { AdminPageTemplate } from './AdminPageTemplate';
import { AdminToolbar } from './AdminToolbar';
import { CreateBucketDialog } from './buckets/CreateBucketDialog';
import { RenameObjectDialog } from './buckets/RenameObjectDialog';
import { UploadObjectsDialog } from './buckets/UploadObjectsDialog';

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
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class AdminModule {
}
