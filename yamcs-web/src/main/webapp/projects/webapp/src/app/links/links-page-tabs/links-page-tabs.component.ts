import { Component } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-links-page-tabs',
  templateUrl: './links-page-tabs.component.html',
  imports: [WebappSdkModule],
})
export class LinksPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {}
}
