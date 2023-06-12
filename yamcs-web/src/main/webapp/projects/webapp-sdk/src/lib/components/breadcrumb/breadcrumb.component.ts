import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'ya-breadcrumb',
  templateUrl: './breadcrumb.component.html',
  styleUrls: ['./breadcrumb.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BreadcrumbComponent {

  @Input()
  icon: string;

  @Input()
  label: string;

  @Input()
  link?: any[] | string;

  @Input()
  action = false;

  @Input()
  queryParams: {
    [k: string]: any;
  };
}
