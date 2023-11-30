import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-clearances-page-tabs',
  templateUrl: './ClearancesPageTabs.html',
  styleUrls: ['./ClearancesPageTabs.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClearancesPageTabs {
  constructor(readonly yamcs: YamcsService) {
  }
}
