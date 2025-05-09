import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DateTimePipe } from '../../pipes/datetime.pipe';

@Component({
  selector: 'ya-table-window',
  templateUrl: './table-window.component.html',
  styleUrl: './table-window.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-table-window',
  },
  imports: [DateTimePipe],
})
export class YaTableWindow {
  duration = input.required<string>();
  start = input<string>();
  stop = input<string>();
}
