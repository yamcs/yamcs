import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-field-divider',
  template: '',
  styleUrl: './field-divider.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-field-divider',
    role: 'separator',
  },
})
export class YaFieldDivider {}
