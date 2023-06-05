import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ParameterValue } from '../../client';

@Component({
  selector: 'ya-expirable',
  templateUrl: './expirable.component.html',
  styleUrls: ['./expirable.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpirableComponent {

  @Input()
  pval: ParameterValue;

  get expired() {
    return this.pval && this.pval.acquisitionStatus === 'EXPIRED';
  }

  getExpiredTooltip() {
    if (this.pval) {
      let msg = 'EXPIRED VALUE.\n';
      msg += `Generated: ${this.pval.generationTime}\n`;
      msg += `Received: ${this.pval.acquisitionTime}`;
      return msg;
    } else {
      return 'EXPIRED VALUE';
    }
  }
}
