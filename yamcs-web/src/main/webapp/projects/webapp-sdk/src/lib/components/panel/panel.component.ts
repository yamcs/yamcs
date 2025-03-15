import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-panel',
  template: '<ng-content />',
  styleUrl: './panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-panel',
  },
})
export class YaPanel {}
