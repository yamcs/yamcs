import { Component, ChangeDetectionStrategy, ViewEncapsulation, HostBinding } from '@angular/core';

/**
 * Applies styling to the attributed table host
 */
@Component({
  selector: 'table[yaSimpleTable]',
  template: '<ng-content></ng-content>',
  styles: [`
    table.ya-simple-table {
      width: 100%;
      overflow: auto;
      border-spacing: 0;
      border-collapse: collapse;
    }

    .ya-simple-table th, .ya-simple-table td {
      border-top: 1px solid rgba(0, 0, 0, 0.03);
      border-bottom: 1px solid rgba(0, 0, 0, 0.03);
    }

    .ya-simple-table th {
      background-color: #f5f5f5;
      font-weight: 400;
      text-align: left;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class YaSimpleTableComponent {

  @HostBinding('class.ya-simple-table')
  applyClass = true;
}
