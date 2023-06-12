import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Service } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-service-state',
  templateUrl: './ServiceState.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServiceState {

  @Input()
  service: Service;
}
