import {
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChildren,
  input,
} from '@angular/core';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { YaIconAction } from '../icon-action/icon-action.component';

@Component({
  selector: 'ya-more',
  templateUrl: 'more.component.html',
  styleUrl: 'more.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [YaIconAction, MatMenu, MatMenuTrigger],
  host: {
    class: 'ya-more',
    '[class.hide]': 'hideIfEmpty() && isEmpty()',
  },
})
export class YaMore {
  disabled = input(false);
  icon = input('more_vert');
  padding = input(true);

  /**
   * If true (default), hide this component when
   * there are no menu items.
   *
   * (useful to avoid permission checks surrounding this
   * component)
   */
  hideIfEmpty = input(true);

  menuItems = contentChildren(MatMenuItem, { descendants: true });
  isEmpty = computed(() => this.menuItems().length === 0);
}
