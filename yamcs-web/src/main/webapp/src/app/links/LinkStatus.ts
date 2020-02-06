import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Link } from '../client';

@Component({
  selector: 'app-link-status',
  templateUrl: './LinkStatus.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkStatus {

  @Input()
  link: Link;

  @Input()
  parentLink: Link;
}
