import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-more',
  templateUrl: 'More.html',
  styleUrls: ['./More.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class More {
}
