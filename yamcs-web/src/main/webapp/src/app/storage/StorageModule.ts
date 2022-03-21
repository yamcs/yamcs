import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { BucketPageTabs } from './buckets/BucketPageTabs';
import { CreateBucketDialog } from './buckets/CreateBucketDialog';
import { CreateFolderDialog } from './buckets/CreateFolderDialog';
import { RenameObjectDialog } from './buckets/RenameObjectDialog';
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
    BucketPageTabs,
    CreateBucketDialog,
    CreateFolderDialog,
    RenameObjectDialog,
    UploadProgressDialog,
    StoragePage,
    StoragePageTemplate,
    StorageToolbar,
  ],
})
export class StorageModule {
}
