import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { ParameterValue } from '../../client';

@Component({
  standalone: true,
  selector: 'ya-expirable',
  templateUrl: './expirable.component.html',
  styleUrl: './expirable.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIcon,
    MatTooltip
  ],
})
export class YaExpirable {

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
