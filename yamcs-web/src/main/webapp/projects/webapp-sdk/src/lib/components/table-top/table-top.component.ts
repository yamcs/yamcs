import { Component, HostBinding, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  standalone: true,
  selector: 'ya-table-top',
  templateUrl: './table-top.component.html',
  styleUrl: './table-top.component.css',
  imports: [
    MatIcon,
  ],
})
export class YaTableTop {

  icon = input<string>('auto');
  severity = input<'info' | 'error'>('info');

  @HostBinding('class.error')
  get error() {
    return this.severity() === 'error';
  }
}
