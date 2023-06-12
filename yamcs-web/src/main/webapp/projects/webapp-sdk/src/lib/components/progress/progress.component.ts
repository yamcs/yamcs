import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'ya-progress',
  templateUrl: './progress.component.html',
  styleUrls: ['./progress.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgressComponent implements OnChanges {

  @Input()
  value: number;

  @Input()
  total: number;

  @Input()
  width: string;

  format = '1.1';

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
