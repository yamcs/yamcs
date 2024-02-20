import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-bucket-page-tabs',
  templateUrl: './BucketPageTabs.html',
  styleUrls: ['./BucketPageTabs.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BucketPageTabs {

  @Input()
  bucket: string;

  constructor(readonly yamcs: YamcsService) {
  }
}
