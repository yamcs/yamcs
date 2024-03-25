import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '@yamcs/webapp-sdk';
import { SharedModule } from '../../shared/SharedModule';

@Component({
  standalone: true,
  selector: 'app-links-page-tabs',
  templateUrl: './links-page-tabs.component.html',
  styleUrl: './links-page-tabs.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SharedModule,
  ],
})
export class LinksPageTabsComponent {
  constructor(readonly yamcs: YamcsService) {
  }
}
