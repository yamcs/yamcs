import { Component, Input } from '@angular/core';
import { Route, YaAttr, YaAttrList } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-route-detail',
  templateUrl: './route-detail.component.html',
  imports: [YaAttr, YaAttrList],
})
export class RouteDetailComponent {
  @Input()
  route: Route;
}
