import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { AlgorithmStatus, OFF_COLOR, ON_COLOR } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-algorithm-status',
  templateUrl: './AlgorithmStatusComponent.html',
  styleUrls: ['./AlgorithmStatusComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmStatusComponent {

  @Input()
  status: AlgorithmStatus;

  @Input()
  size = 14;

  onColor = ON_COLOR;
  offColor = OFF_COLOR;
}
