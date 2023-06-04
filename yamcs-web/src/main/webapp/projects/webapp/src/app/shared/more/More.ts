import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-more',
  templateUrl: 'More.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class More {

  @Input()
  icon = 'more_vert';
}
