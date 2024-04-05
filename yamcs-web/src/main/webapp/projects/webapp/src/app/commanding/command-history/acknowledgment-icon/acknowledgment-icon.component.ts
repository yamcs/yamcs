import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Acknowledgment, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-acknowledgment-icon',
  templateUrl: './acknowledgment-icon.component.html',
  styleUrl: './acknowledgment-icon.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AcknowledgmentIconComponent {

  @Input()
  ack: Acknowledgment;
}
