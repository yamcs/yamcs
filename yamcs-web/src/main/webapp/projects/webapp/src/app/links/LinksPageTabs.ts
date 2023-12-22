import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-links-page-tabs',
  templateUrl: './LinksPageTabs.html',
  styleUrls: ['./LinksPageTabs.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinksPageTabs {
  constructor(readonly yamcs: YamcsService) {
  }
}
