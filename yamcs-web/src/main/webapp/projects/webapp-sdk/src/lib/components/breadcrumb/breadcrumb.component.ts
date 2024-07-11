
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

@Component({
  standalone: true,
  selector: 'ya-breadcrumb',
  templateUrl: './breadcrumb.component.html',
  styleUrl: './breadcrumb.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIcon,
    RouterLink
],
})
export class YaBreadcrumb {

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
