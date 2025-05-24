import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type YaPanelPadding = 'small' | 'default';

@Component({
  selector: 'ya-panel',
  template: '<ng-content />',
  styleUrl: './panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-panel',
    '[class.small-padding]': 'padding() === "small"',
  },
})
export class YaPanel {
  padding = input<YaPanelPadding>('default');
}
