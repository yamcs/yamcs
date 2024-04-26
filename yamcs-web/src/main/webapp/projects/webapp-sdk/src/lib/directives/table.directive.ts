import { ChangeDetectionStrategy, Component, HostBinding, ViewEncapsulation } from '@angular/core';

/**
 * Applies styling to the attributed table host
 */
@Component({
  standalone: true,
  selector: 'table[yaTable]',
  template: '<ng-content />',
  styles: `
    table.ya-table {
      border-spacing: 0;
      border-collapse: collapse;
      border-radius: 2px;
      box-shadow: 0 2px 2px rgba(0, 0, 0, .24), 0 0 2px rgba(0, 0, 0, .12);
      margin-bottom: 32px;
    }

    .ya-table th, .ya-table td {
      padding: 13px 32px;
      border: 1px solid rgba(0, 0, 0, 0.03);
    }

    .ya-table th {
      background-color: var(--y-background-color);
      font-weight: 400;
      text-align: left;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class TableDirective {

  @HostBinding('class.ya-table')
  applyClass = true;
}
