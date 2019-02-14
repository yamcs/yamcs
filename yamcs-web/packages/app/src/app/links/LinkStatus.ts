import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Link } from '@yamcs/client';

@Component({
  selector: 'app-link-status',
  templateUrl: './LinkStatus.html',
  styleUrls: ['./LinkStatus.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinkStatus {

  @Input()
  link: Link;

  @Input()
  parentLink: Link;
}
