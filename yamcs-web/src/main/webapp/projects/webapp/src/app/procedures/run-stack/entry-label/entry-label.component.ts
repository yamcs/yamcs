import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

@Component({
  standalone: true,
  selector: 'app-entry-label',
  templateUrl: './entry-label.component.html',
  styleUrl: './entry-label.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatIcon,
  ],
})
export class EntryLabel {

  icon = input.required<string>();
  text = input.required<string>();
}
