import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Service, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-service-state',
  templateUrl: './service-state.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ServiceStateComponent {

  @Input()
  service: Service;
}
