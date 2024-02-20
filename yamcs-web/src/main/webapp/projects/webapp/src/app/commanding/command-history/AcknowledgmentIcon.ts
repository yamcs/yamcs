import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Acknowledgment } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-acknowledgment-icon',
  templateUrl: './AcknowledgmentIcon.html',
  styleUrls: ['./AcknowledgmentIcon.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AcknowledgmentIcon {

  @Input()
  ack: Acknowledgment;
}
