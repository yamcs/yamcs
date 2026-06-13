import { Component, Input } from '@angular/core';
import { Service, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-service-state',
  templateUrl: './service-state.component.html',
  styleUrl: './service-state.component.css',
  imports: [WebappSdkModule],
})
export class ServiceStateComponent {
  @Input()
  service: Service;
}
