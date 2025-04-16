import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'ya-button-group',
  templateUrl: './button-group.component.html',
  styleUrl: './button-group.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'ya-button-group',
  },
})
export class YaButtonGroup {}
