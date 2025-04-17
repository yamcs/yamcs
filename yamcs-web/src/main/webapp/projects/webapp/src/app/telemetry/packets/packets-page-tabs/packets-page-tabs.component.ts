import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-packets-page-tabs',
  templateUrl: './packets-page-tabs.component.html',
  styleUrl: './packets-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class PacketsPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {}
}
