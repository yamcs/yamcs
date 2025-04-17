import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type YaToolbarAppearance = 'top' | 'bottom';

export type YaToolbarAlign = 'left' | 'center';

@Component({
  selector: 'ya-toolbar',
  templateUrl: 'toolbar.component.html',
  styleUrl: './toolbar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-toolbar',
    '[class.ya-toolbar-bottom]': "appearance() === 'bottom'",
    '[class.ya-toolbar-center]': "align() === 'center'",
  },
})
export class YaToolbar {
  appearance = input<YaToolbarAppearance>('top');
  align = input<YaToolbarAlign>('left');
}
