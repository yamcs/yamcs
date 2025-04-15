import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-vertical-divider',
  template: '',
  styleUrl: './vertical-divider.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-vertical-divider',
    role: 'separator',
  },
})
export class YaVerticalDivider {}
