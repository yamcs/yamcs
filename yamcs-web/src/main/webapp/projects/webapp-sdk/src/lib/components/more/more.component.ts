import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatMenu, MatMenuTrigger } from '@angular/material/menu';
import { YaIconAction } from '../icon-action/icon-action.component';

@Component({
  standalone: true,
  selector: 'ya-more',
  templateUrl: 'more.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    YaIconAction,
    MatMenu,
    MatMenuTrigger,
  ],
})
export class YaMore {

  @Input()
  icon = 'more_vert';

  @Input()
  padding = true;
}
