import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-alarms-page-tabs',
  templateUrl: './alarms-page-tabs.component.html',
  styleUrl: './alarms-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AlarmsPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {
  }
}
