import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type YaToolbarAppearance = 'top' | 'bottom';

@Component({
  selector: 'ya-toolbar',
  templateUrl: 'toolbar.component.html',
  styleUrl: './toolbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-toolbar',
    '[class.ya-toolbar-bottom]': "appearance() === 'bottom'",
  },
})
export class YaToolbar {
  appearance = input<YaToolbarAppearance>('top');
}
