import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-filter-bar',
  templateUrl: './filter-bar.component.html',
  styleUrl: './filter-bar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-filter-bar',
  },
})
export class YaFilterBar {}
