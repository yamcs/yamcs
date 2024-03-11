import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-gaps-page-tabs',
  templateUrl: './GapsPageTabs.html',
  styleUrl: './GapsPageTabs.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GapsPageTabs {
  constructor(readonly yamcs: YamcsService) {
  }
}
