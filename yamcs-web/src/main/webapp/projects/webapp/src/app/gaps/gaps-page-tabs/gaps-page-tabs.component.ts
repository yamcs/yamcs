import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-gaps-page-tabs',
  templateUrl: './gaps-page-tabs.component.html',
  styleUrl: './gaps-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class GapsPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {
  }
}
