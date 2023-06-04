import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-breadcrumb',
  templateUrl: './Breadcrumb.html',
  styleUrls: ['./Breadcrumb.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Breadcrumb {

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
