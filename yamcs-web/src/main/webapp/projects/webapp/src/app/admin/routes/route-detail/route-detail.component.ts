import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Route, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-route-detail',
  templateUrl: './route-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class RouteDetailComponent {

  @Input()
  route: Route;
}
