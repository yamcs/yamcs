import { Component, inject, input } from '@angular/core';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

/**
 * Tabs for switching between activities, triggers and trigger firings.
 * Only shown when triggers are available.
 */
@Component({
  selector: 'app-activities-page-tabs',
  templateUrl: './activities-page-tabs.component.html',
  styleUrl: './activities-page-tabs.component.css',
  imports: [WebappSdkModule],
})
export class ActivitiesPageTabsComponent {
  readonly yamcs = inject(YamcsService);

  pendingConfirmations = input<number>(0);
}
