import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Bucket, StorageClient, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  templateUrl: './BucketPropertiesPage.html',
  styleUrls: ['./BucketPropertiesPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BucketPropertiesPage {

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
