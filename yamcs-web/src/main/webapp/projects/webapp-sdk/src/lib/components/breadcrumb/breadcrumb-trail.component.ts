import { Component, Input } from '@angular/core';

@Component({
  selector: 'ya-breadcrumb-trail',
  templateUrl: './breadcrumb-trail.component.html',
  styleUrl: './breadcrumb-trail.component.css',
})
export class YaBreadcrumbTrail {
  @Input()
  showMargin = true;
}
