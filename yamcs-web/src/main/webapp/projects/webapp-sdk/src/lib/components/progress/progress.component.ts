import { AsyncPipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  numberAttribute,
  OnChanges,
} from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'ya-progress',
  templateUrl: './progress.component.html',
  styleUrl: './progress.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-progress',
    '[style.height.px]': 'height',
    '[style.width.px]': 'width',
    // Subtract 2 to account for borders
    '[style.lineHeight.px]': 'height - 2',
  },
  imports: [AsyncPipe, DecimalPipe],
})
export class YaProgress implements OnChanges {
  @Input({ transform: numberAttribute })
  value: number;

  @Input({ transform: numberAttribute })
  total: number;

  @Input({ transform: numberAttribute })
  height: number = 16;

  @Input({ transform: numberAttribute })
  width: number = 100;

  @Input()
  format = '1.1';

  @Input()
  unit = '%';

  ratio$ = new BehaviorSubject<number | null>(null);
  boundedRatio$ = new BehaviorSubject<number>(0);

  ngOnChanges() {
    const ratio = this.value / this.total;
    if (ratio === null || ratio === undefined) {
      this.ratio$.next(null);
      this.boundedRatio$.next(0);
      return;
    }

    this.ratio$.next(ratio);
    this.boundedRatio$.next(Math.max(0, Math.min(1, ratio)));
  }
}
