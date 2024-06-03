import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Bucket, StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { StoragePageTemplateComponent } from '../../storage-page-template/storage-page-template.component';
import { StorageToolbarComponent } from '../../storage-toolbar/storage-toolbar.component';
import { BucketPageTabsComponent } from '../bucket-page-tabs/bucket-page-tabs.component';

@Component({
  standalone: true,
  templateUrl: './bucket-properties.component.html',
  styleUrl: './bucket-properties.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BucketPageTabsComponent,
    WebappSdkModule,
    StoragePageTemplateComponent,
    StorageToolbarComponent,
  ],
})
export class BucketPropertiesComponent {

  name: string;

  bucket$ = new BehaviorSubject<Bucket | null>(null);
  private storageClient: StorageClient;

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
    title: Title,
  ) {
    this.name = route.snapshot.parent!.paramMap.get('name')!;
    title.setTitle(this.name + ': Properties');
    this.storageClient = yamcs.createStorageClient();
    this.storageClient.getBucket(this.name).then(bucket => {
      this.bucket$.next(bucket);
    });
  }

  bucketSizePercentage(bucket: Bucket, ceil = false) {
    var pct = 100 * bucket.size / bucket.maxSize;
    return ceil ? Math.min(100, pct) : pct;
  }

  objectCountPercentage(bucket: Bucket, ceil = false) {
    var pct = 100 * bucket.numObjects / bucket.maxObjects;
    return ceil ? Math.min(100, pct) : pct;
  }

  zeroOrMore(value: number) {
    return Math.max(0, value);
  }
}
