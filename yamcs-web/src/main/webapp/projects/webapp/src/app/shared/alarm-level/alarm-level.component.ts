import { NgSwitch, NgSwitchCase } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  standalone: true,
  selector: 'app-alarm-level',
  templateUrl: './alarm-level.component.html',
  styleUrl: './alarm-level.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgSwitch,
    NgSwitchCase,
  ],
})
export class AlarmLevelComponent {

  @Input()
  level: string;

  @Input()
  grayscale = false;
}
