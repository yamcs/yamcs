import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { AcknowledgeInfo, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-advance-ack-help',
  templateUrl: './advance-ack-help.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AdvanceAckHelpComponent {

  @Input()
  verifiers: AcknowledgeInfo[] = [];

  @Input()
  extra: AcknowledgeInfo[] = [];
}
