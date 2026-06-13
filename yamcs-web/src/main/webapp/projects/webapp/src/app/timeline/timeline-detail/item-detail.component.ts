import { Component, input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { ItemState } from '../ItemState';
import { ItemDetailTabLogsComponent } from './item-detail-tab-logs.component';
import { ItemDetailTabSummaryComponent } from './item-detail-tab-summary.component';

@Component({
  selector: 'app-item-detail',
  templateUrl: './item-detail.component.html',
  imports: [
    ItemDetailTabLogsComponent,
    ItemDetailTabSummaryComponent,
    WebappSdkModule,
  ],
})
export class ItemDetailComponent {
  item = input<ItemState>();
}
