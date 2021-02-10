import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CreateBucketDialog } from './buckets/CreateBucketDialog';
import { RenameObjectDialog } from './buckets/RenameObjectDialog';
import { UploadObjectsDialog } from './buckets/UploadObjectsDialog';
import { UploadProgressDialog } from './buckets/UploadProgressDialog';
import { StoragePage } from './StoragePage';
import { StoragePageTemplate } from './StoragePageTemplate';
import { routingComponents, StorageRoutingModule } from './StorageRoutingModule';
import { StorageToolbar } from './StorageToolbar';

@NgModule({
  imports: [
    SharedModule,
    StorageRoutingModule,
  ],
  declarations: [
    routingComponents,
    CreateBucketDialog,
    RenameObjectDialog,
    UploadObjectsDialog,
    UploadProgressDialog,
    StoragePage,
    StoragePageTemplate,
    StorageToolbar,
  ],
})
export class StorageModule {
}
