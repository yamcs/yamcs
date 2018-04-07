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
      border: 0 1px solid rgba(0, 0, 0, 0.03);
      font-size: 12px;
      line-height: 16px;
      padding-top: 0;
      padding-bottom: 0;
      padding-left: 8px;
      padding-right: 0;
    }

    .ya-data-table th {
      text-align: left;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.654902);
      border-bottom: 1px solid rgba(0, 0, 0, 0.156863);
    }

    .ya-data-table th.lcolumn {
      border-bottom: 1.1px solid rgba(0, 0, 0, .08);
    }

    .ya-data-table td {
      color: rgba(0, 0, 0, .654);
      padding: 7px 0 8px 8px;
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
