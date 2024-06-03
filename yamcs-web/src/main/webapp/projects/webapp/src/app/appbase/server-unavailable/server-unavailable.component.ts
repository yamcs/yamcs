import { ChangeDetectionStrategy, Component } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { OopsComponent } from '../oops/oops.component';

@Component({
  standalone: true,
  templateUrl: './server-unavailable.component.html',
  styleUrl: './server-unavailable.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    OopsComponent,
    WebappSdkModule,
  ],
})
export class ServerUnavailableComponent {

  reload() {
    window.location.reload();
  }
}
