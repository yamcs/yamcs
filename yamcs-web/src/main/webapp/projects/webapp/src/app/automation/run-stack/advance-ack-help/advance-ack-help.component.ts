import { Component, Input } from '@angular/core';
import { AcknowledgmentInfo, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-advance-ack-help',
  templateUrl: './advance-ack-help.component.html',
  imports: [WebappSdkModule],
})
export class AdvanceAckHelpComponent {
  @Input()
  verifiers: AcknowledgmentInfo[] = [];

  @Input()
  extra: AcknowledgmentInfo[] = [];
}
