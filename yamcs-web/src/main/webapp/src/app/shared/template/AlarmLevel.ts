import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

@Component({
  selector: 'app-alarm-level',
  templateUrl: './AlarmLevel.html',
  styleUrls: ['./AlarmLevel.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlarmLevel {

  @Input()
  level: string;

  @Input()
  grayscale = false;
}
