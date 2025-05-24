import { DecimalPipe } from '@angular/common';
import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  numberAttribute,
} from '@angular/core';

export type YaProgressMode = 'determinate' | 'indeterminate';

@Component({
  selector: 'ya-progress',
  templateUrl: './progress.component.html',
  styleUrl: './progress.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-progress',
    '[class.alarm]': 'isAlarm()',
    '[style.height.px]': 'height()',
    '[style.width.px]': 'width()',
  },
  imports: [DecimalPipe],
  providers: [DecimalPipe],
})
export class YaProgress {
  private decimalPipe = inject(DecimalPipe);

  mode = input<YaProgressMode>('determinate');
  value = input(0, { transform: numberAttribute });
  total = input(1, { transform: numberAttribute });
  height = input(16, { transform: numberAttribute });
  width = input(100, { transform: numberAttribute });
  format = input('1.2-2');
  unit = input('%');

  /**
   * Whether to change colors if the value exceeds the total
   */
  alarmSensitive = input(false, { transform: booleanAttribute });

  ratio = computed(() => {
    const ratio = this.value() / this.total();
    return Math.max(0, ratio);
  });
  percentage = computed(() => 100 * this.ratio());

  clampedRatio = computed(() => {
    const ratio = this.value() / this.total();
    return Math.max(0, Math.min(1, ratio));
  });
  clampedPercentage = computed(() => 100 * this.clampedRatio());

  /**
   * True if the value exceeds the total
   */
  isAlarm = computed(() => {
    const alarmSensitive = this.alarmSensitive();
    const ratio = this.ratio();
    if (alarmSensitive) {
      return ratio > 1;
    } else {
      return false;
    }
  });

  displayLabel = computed(() => {
    const value = this.value();
    const ratio = this.ratio();
    const clampedPercentage = this.clampedPercentage();
    const format = this.format();
    const unit = this.unit();
    if (value === null || value === undefined || isNaN(value)) {
      return '';
    } else {
      const valueStr = this.decimalPipe.transform(clampedPercentage, format);
      return (ratio > 1 ? '> ' : '') + valueStr + unit;
    }
  });
}
