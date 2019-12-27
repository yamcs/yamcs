import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Acknowledgment } from './Acknowledgment';

@Component({
  selector: 'app-acknowledgment-icon',
  templateUrl: './AcknowledgmentIcon.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AcknowledgmentIcon {

  @Input()
  ack: Acknowledgment;
}
