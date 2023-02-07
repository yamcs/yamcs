import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-breadcrumb-trail',
  templateUrl: './BreadcrumbTrail.html',
  styleUrls: ['./BreadcrumbTrail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BreadcrumbTrail {
  @Input()
  showMargin = true;
}
