import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-clearances-page-tabs',
  templateUrl: './clearances-page-tabs.component.html',
  styleUrl: './clearances-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ClearancesPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {}
}
