import { Component, Input } from '@angular/core';
import { Acknowledgment, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-acknowledgment-icon',
  templateUrl: './acknowledgment-icon.component.html',
  styleUrl: './acknowledgment-icon.component.css',
  imports: [WebappSdkModule],
})
export class AcknowledgmentIconComponent {
  @Input()
  ack: Acknowledgment;
}
