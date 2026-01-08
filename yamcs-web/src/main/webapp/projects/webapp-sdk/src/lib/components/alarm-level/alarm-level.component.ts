import { NgClass, NgTemplateOutlet } from '@angular/common';
import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  Input,
} from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
  selector: 'ya-alarm-level',
  templateUrl: './alarm-level.component.html',
  styleUrl: './alarm-level.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTooltip, NgClass, NgTemplateOutlet],
})
export class YaAlarmLevel {
  @Input()
  level: string;

  @Input({ transform: booleanAttribute })
  grayscale = false;
}
