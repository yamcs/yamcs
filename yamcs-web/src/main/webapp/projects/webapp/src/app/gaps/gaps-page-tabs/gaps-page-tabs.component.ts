import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-gaps-page-tabs',
  templateUrl: './gaps-page-tabs.component.html',
  styleUrl: './gaps-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class GapsPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {
  }
}
