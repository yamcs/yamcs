
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
  standalone: true,
  selector: 'app-significance-level',
  templateUrl: './significance-level.component.html',
  styleUrl: './significance-level.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatTooltip,
    NgClass,
    NgTemplateOutlet,
  ],
})
export class SignificanceLevelComponent {

  @Input()
  level: string;

  @Input()
  grayscale = false;
}
