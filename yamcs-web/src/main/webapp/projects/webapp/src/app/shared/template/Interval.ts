import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-interval',
  templateUrl: './Interval.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Interval implements OnChanges {

  @Input()
  left: number;

  @Input()
  right: number;

  @Input()
  leftInclusive = true;

  @Input()
  rightInclusive = true;

  @Input()
  singleValueIfEqual = false;

  interval$ = new BehaviorSubject<string | null>(null);

  ngOnChanges() {
    let result;
    if (this.singleValueIfEqual && this.left !== undefined && this.left === this.right) {
      result = String(this.left);
    } else {
      result = '(-∞';
      if (this.left !== undefined) {
        result = this.leftInclusive ? '[' : '(';
        result += this.left;
      }

      result += ', ';

      if (this.right !== undefined) {
        result += this.right;
        result += this.rightInclusive ? ']' : ')';
      } else {
        result += '+∞)';
      }
    }

    this.interval$.next(result);
  }
}
