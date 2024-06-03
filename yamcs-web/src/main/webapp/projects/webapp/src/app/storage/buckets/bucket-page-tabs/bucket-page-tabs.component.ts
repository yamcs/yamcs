import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-bucket-page-tabs',
  templateUrl: './bucket-page-tabs.component.html',
  styleUrl: './bucket-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class BucketPageTabsComponent {

  @Input()
  bucket: string;

  constructor(readonly yamcs: YamcsService) {
  }
}
