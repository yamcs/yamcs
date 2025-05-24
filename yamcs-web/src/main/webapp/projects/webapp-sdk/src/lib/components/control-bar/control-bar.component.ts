import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * A bar for normal-sized controls (buttons)
 */
@Component({
  selector: 'ya-control-bar',
  templateUrl: './control-bar.component.html',
  styleUrl: './control-bar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-control-bar',
  },
})
export class YaControlBar {}
