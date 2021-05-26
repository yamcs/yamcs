import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Route } from '../../client';

@Component({
  selector: 'app-route-detail',
  templateUrl: './RouteDetail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RouteDetail {

  @Input()
  route: Route;
}
