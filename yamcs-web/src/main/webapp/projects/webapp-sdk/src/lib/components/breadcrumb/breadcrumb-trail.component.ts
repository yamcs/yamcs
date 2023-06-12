import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'ya-breadcrumb-trail',
  templateUrl: './breadcrumb-trail.component.html',
  styleUrls: ['./breadcrumb-trail.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BreadcrumbTrailComponent {
  @Input()
  showMargin = true;
}
