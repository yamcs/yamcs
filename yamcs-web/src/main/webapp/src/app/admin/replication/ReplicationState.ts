import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-replication-state',
  templateUrl: './ReplicationState.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReplicationState {

  @Input()
  connected: boolean;
}
