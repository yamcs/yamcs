import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-alarms-page-tabs',
  templateUrl: './alarms-page-tabs.component.html',
  styleUrl: './alarms-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class AlarmsPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {
  }
}
