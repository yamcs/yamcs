import { NgClass, NgTemplateOutlet } from '@angular/common';
import { Component, Input } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
  selector: 'app-event-severity',
  templateUrl: './event-severity.component.html',
  styleUrl: './event-severity.component.css',
  imports: [MatTooltip, NgClass, NgTemplateOutlet],
})
export class EventSeverityComponent {
  @Input()
  severity: string;

  @Input()
  grayscale = false;
}
