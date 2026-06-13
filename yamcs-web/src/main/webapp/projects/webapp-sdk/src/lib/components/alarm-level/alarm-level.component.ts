import { NgClass, NgTemplateOutlet } from '@angular/common';
import { booleanAttribute, Component, Input } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
  selector: 'ya-alarm-level',
  templateUrl: './alarm-level.component.html',
  styleUrl: './alarm-level.component.css',
  imports: [MatTooltip, NgClass, NgTemplateOutlet],
})
export class YaAlarmLevel {
  @Input()
  level: string;

  @Input({ transform: booleanAttribute })
  grayscale = false;
}
