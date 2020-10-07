import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { StackEntry } from './StackEntry';

@Component({
  selector: 'app-stacked-command-detail',
  templateUrl: './StackedCommandDetail.html',
  styleUrls: ['./StackedCommandDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StackedCommandDetail {

  @Input()
  entry: StackEntry;
}
