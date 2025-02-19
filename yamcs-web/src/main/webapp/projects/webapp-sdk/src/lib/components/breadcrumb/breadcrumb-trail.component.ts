import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'ya-breadcrumb-trail',
  templateUrl: './breadcrumb-trail.component.html',
  styleUrl: './breadcrumb-trail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaBreadcrumbTrail {
  @Input()
  showMargin = true;
}
