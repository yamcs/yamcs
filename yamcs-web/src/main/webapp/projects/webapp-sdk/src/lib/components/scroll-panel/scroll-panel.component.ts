import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-scroll-panel',
  templateUrl: './scroll-panel.component.html',
  styleUrl: './scroll-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-scroll-panel',
  },
})
export class YaScrollPanel {}
