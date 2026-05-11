import {
  ChangeDetectionStrategy,
  Component,
  Input,
  ViewEncapsulation,
} from '@angular/core';

@Component({
  selector: 'ul[yaTagBar]',
  template: '<ng-content />',
  host: {
    class: 'ya-tag-bar',
    '[class.ya-tag-bar-truncated]': 'isTruncated',
  },
  styles: `
    ul.ya-tag-bar {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      align-items: center;
    }

    ul.ya-tag-bar.ya-tag-bar-truncated {
      display: flex;
      flex-wrap: nowrap;
      overflow: hidden;
      max-height: 24px;
      mask-image: linear-gradient(to right, black 85%, transparent 100%);
      position: relative;
    }

    td ul.ya-tag-bar {
      margin-top: -4px;
      margin-bottom: -4px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class YaTagBar {
  @Input('yaTagBarTruncate')
  set truncation(value: boolean | string) {
    this.isTruncated = value !== false && value !== 'false';
  }

  isTruncated = false;
}
