import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Route } from '@yamcs/client';

@Component({
  selector: 'app-route-detail',
  templateUrl: './RouteDetail.html',
  styleUrls: ['./RouteDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteDetail {

  @Input()
  route: Route;
}
