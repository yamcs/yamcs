import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-attr-divider',
  template: '',
  styleUrl: './attr-divider.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-attr-divider',
    role: 'separator',
  },
})
export class YaAttrDivider {}
