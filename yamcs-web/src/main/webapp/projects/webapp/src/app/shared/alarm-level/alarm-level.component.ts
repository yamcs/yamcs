
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
  standalone: true,
  selector: 'app-alarm-level',
  templateUrl: './alarm-level.component.html',
  styleUrl: './alarm-level.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatTooltip,
  ],
})
export class AlarmLevelComponent {

  @Input()
  level: string;

  @Input()
  grayscale = false;
}
