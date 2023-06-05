import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'ya-more',
  templateUrl: 'more.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MoreComponent {

  @Input()
  icon = 'more_vert';
}
