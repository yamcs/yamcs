import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-alarm-level',
  templateUrl: './AlarmLevel.html',
  styleUrl: './AlarmLevel.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmLevel {

  @Input()
  level: string;

  @Input()
  grayscale = false;
}
