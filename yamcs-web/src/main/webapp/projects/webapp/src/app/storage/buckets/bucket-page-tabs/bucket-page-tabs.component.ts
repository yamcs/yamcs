import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { YamcsService } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-bucket-page-tabs',
  templateUrl: './bucket-page-tabs.component.html',
  styleUrl: './bucket-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class BucketPageTabsComponent {

  @Input()
  bucket: string;

  constructor(readonly yamcs: YamcsService) {
  }
}
