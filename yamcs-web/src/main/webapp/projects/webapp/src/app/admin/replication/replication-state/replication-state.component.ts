import { Component, input } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-replication-state',
  templateUrl: './replication-state.component.html',
  styleUrl: './replication-state.component.css',
  imports: [WebappSdkModule],
})
export class ReplicationStateComponent {
  connected = input<boolean>();
}
