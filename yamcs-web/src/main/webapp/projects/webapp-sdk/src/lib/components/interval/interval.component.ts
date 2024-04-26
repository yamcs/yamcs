import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type RangeForm = 'inside' | 'outside';

@Component({
  standalone: true,
  selector: 'ya-interval',
  templateUrl: './interval.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AsyncPipe,
  ],
})
export class YaInterval implements OnChanges {

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

  @Input()
  outside = false;

  interval$ = new BehaviorSubject<string | null>(null);

  ngOnChanges() {
    const result = this.outside ? this.printOutsideForm() : this.printInsideForm();
    this.interval$.next(result);
  }

  private printInsideForm() {
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
    return result;
  }

  private printOutsideForm() {
    let result = '';
    if (this.left !== undefined) {
      result += '(-∞, ';
      result += this.left;
      result += this.leftInclusive ? ')' : ']';

      if (this.right !== undefined) {
        result += ', ';
      }
    }

    if (this.right !== undefined) {
      result += this.rightInclusive ? '(' : '[';
      result += this.right;
      result += ', +∞)';
    }

    return result;
  }
}
