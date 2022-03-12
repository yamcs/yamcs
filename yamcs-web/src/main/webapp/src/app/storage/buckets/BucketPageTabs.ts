import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-bucket-page-tabs',
  templateUrl: './BucketPageTabs.html',
  styleUrls: ['./BucketPageTabs.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BucketPageTabs {
  constructor(readonly yamcs: YamcsService) {
  }
}
