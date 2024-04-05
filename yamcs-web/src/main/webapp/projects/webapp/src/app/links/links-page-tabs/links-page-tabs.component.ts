import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-links-page-tabs',
  templateUrl: './links-page-tabs.component.html',
  styleUrl: './links-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class LinksPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {
  }
}
