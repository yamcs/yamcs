import { Component, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  selector: 'ya-table-top',
  templateUrl: './table-top.component.html',
  styleUrl: './table-top.component.css',
  host: {
    '[class.error]': 'error',
  },
  imports: [MatIcon],
})
export class YaTableTop {
  icon = input<string>('auto');
  severity = input<'info' | 'error'>('info');

  get error() {
    return this.severity() === 'error';
  }
}
