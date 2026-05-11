import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
} from '@angular/core';

@Component({
  selector: 'li[yaTag]',
  template: '<ng-content />',
  host: { class: 'ya-tag' },
  styles: `
    li.ya-tag {
      background-color: #e8eaed;
      color: #3c4043;
      font-size: 10px;
      font-family: 'Roboto', sans-serif;
      font-weight: 400;
      line-height: 16px;
      padding: 3px 10px;
      border-radius: 12px;
      border: none;
      display: inline-flex;
      align-items: center;
      white-space: nowrap;
      cursor: default;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class YaTag {}
