import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { ParameterValue } from '../../client';

@Component({
  selector: 'ya-parameter-status',
  templateUrl: './parameter-status.component.html',
  styleUrl: './parameter-status.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-parameter-status',
    '[class.invalid]': 'invalid()',
    '[class.expired]': 'expired()',
    '[style.gap]': 'gap()',
  },
  imports: [MatIcon, MatTooltip],
})
export class YaParameterStatus {
  pval = input<ParameterValue>();
  showLowHigh = input(true, { transform: booleanAttribute });
  gap = input('5px');

  low = computed(() => {
    return this.pval()?.rangeCondition === 'LOW';
  });

  high = computed(() => {
    return this.pval()?.rangeCondition === 'HIGH';
  });

  invalid = computed(() => {
    return this.pval()?.acquisitionStatus === 'INVALID';
  });

  expired = computed(() => {
    return this.pval()?.acquisitionStatus === 'EXPIRED';
  });

  expiredTooltip = computed(() => {
    const pval = this.pval();
    if (pval) {
      let msg = 'Expired.\n';
      msg += `Generated: ${pval.generationTime}\n`;
      msg += `Received: ${pval.acquisitionTime}`;
      return msg;
    } else {
      return 'Expired';
    }
  });
}
