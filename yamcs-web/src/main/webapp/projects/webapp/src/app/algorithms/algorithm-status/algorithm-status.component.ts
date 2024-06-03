import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { AlgorithmStatus, OFF_COLOR, ON_COLOR, WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-algorithm-status',
  templateUrl: './algorithm-status.component.html',
  styleUrl: './algorithm-status.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class AlgorithmStatusComponent {

  @Input()
  status: AlgorithmStatus;

  @Input()
  size = 14;

  onColor = ON_COLOR;
  offColor = OFF_COLOR;
}
