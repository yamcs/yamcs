import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { StartCondition, WebappSdkModule } from '@yamcs/webapp-sdk';
import { ItemState } from '../ItemState';

@Component({
  selector: 'app-item-detail-tab-summary',
  templateUrl: './item-detail-tab-summary.component.html',
  styleUrl: './item-detail-tab-summary.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ItemDetailTabSummaryComponent {
  item = input.required<ItemState>();

  activity = computed(() => this.item().lastRun);

  describeStartCondition(startCondition: StartCondition) {
    const label = startCondition.replace('_', ' ').toLowerCase();
    return label[0].toUpperCase() + label.slice(1);
  }
}
