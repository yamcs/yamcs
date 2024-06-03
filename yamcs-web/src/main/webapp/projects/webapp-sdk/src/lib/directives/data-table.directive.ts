import { ChangeDetectionStrategy, Component, HostBinding, ViewEncapsulation } from '@angular/core';

/**
 * Applies styling to the attributed table host
 */
@Component({
  standalone: true,
  selector: 'table[yaDataTable]',
  template: '<ng-content />',
  styles: `
    table.ya-data-table {
      border-spacing: 0;
      border-collapse: collapse;
      border-top: 1px solid rgba(0, 0, 0, 0.1);
    }

    .ya-data-table th, .ya-data-table td {
      font-size: 12px;
      line-height: 16px;
      padding: 4px 8px 4px 0;
    }

    .ya-data-table th {
      text-align: left;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.654902);
      border-bottom: 1px solid rgba(0, 0, 0, 0.1);
    }

    .ya-data-table th.lcolumn {
      border-bottom: 1px solid rgba(0, 0, 0, 0.1);
    }

    .ya-data-table td {
      color: rgba(0, 0, 0, .654);
      border-bottom: 1px solid rgba(0, 0, 0, 0.1);
      background-color: #fff;
    }

    .ya-data-table td.wrap200 {
      min-width: 200px;
      white-space: normal;
      word-break: break-word;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class DataTableDirective {

  @HostBinding('class.ya-data-table')
  applyClass = true;
}
