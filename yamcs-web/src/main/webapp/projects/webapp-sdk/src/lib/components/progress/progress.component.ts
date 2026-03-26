import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  numberAttribute,
} from '@angular/core';

@Component({
  selector: 'ya-progress',
  templateUrl: './progress.component.html',
  styleUrl: './progress.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-progress',
    '[style.height.px]': 'height()',
    '[style.width.px]': 'width()',
  },
  imports: [DecimalPipe],
})
export class YaProgress {
  value = input(0, { transform: numberAttribute });
  total = input(1, { transform: numberAttribute });
  height = input(16, { transform: numberAttribute });
  width = input(100, { transform: numberAttribute });
  format = input('1.2-2');
  unit = input('%');

  ratio = computed(() => {
    const ratio = this.value() / this.total();
    return Math.max(0, Math.min(1, ratio));
  });
  percentage = computed(() => 100 * this.ratio());
}
