import { Component, ChangeDetectionStrategy, ViewEncapsulation, HostBinding } from '@angular/core';

/**
 * Applies styling to the attributed table host
 */
@Component({
  selector: 'table[yaDataTable]',
  template: '<ng-content></ng-content>',
  styles: [`
    table.ya-data-table {
      border-spacing: 0;
      border-collapse: collapse;
    }

    .ya-data-table th, .ya-data-table td {
      padding: 0;
      border: 0 1px solid rgba(0, 0, 0, 0.03);
      font-size: 12px;
      line-height: 16px;
    }

    .ya-data-table th {
      text-align: left;
      font-weight: 500;
    }

    .ya-data-table td {
      color: rgba(0, 0, 0, .654);
      padding: 7px 0 8px 0;
      border-bottom: 1.1px solid rgba(0, 0, 0, .08);
    }

    .ya-data-table td:not(:first-child), .ya-data-table th:not(:first-child) {
      padding-left: 24px;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class YaDataTableComponent {

  @HostBinding('class.ya-data-table')
  applyClass = true;
}
