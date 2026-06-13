import { Component } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-alarms-page-tabs',
  templateUrl: './alarms-page-tabs.component.html',
  styleUrl: './alarms-page-tabs.component.css',
  imports: [WebappSdkModule],
})
export class AlarmsPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {}
}
