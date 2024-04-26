import { ChangeDetectionStrategy, Component, HostBinding, ViewEncapsulation } from '@angular/core';

/**
 * Applies styling to the attributed table host
 */
@Component({
  standalone: true,
  selector: 'table[yaSimpleTable]',
  template: '<ng-content />',
  styles: `
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
      background-color: var(--y-background-color);
      text-align: left;
      font-weight: 500;
      color: black;
      width: 150px;
      vertical-align: top;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class SimpleTableDirective {

  @HostBinding('class.ya-simple-table')
  applyClass = true;
}
