import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-events-page-tabs',
  templateUrl: './events-page-tabs.component.html',
  styleUrl: './events-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class EventsPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {
  }
}
