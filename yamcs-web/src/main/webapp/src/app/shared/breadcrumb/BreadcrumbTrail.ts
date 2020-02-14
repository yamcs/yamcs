import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-breadcrumb-trail',
  templateUrl: './BreadcrumbTrail.html',
  styleUrls: ['./BreadcrumbTrail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BreadcrumbTrail {
}
