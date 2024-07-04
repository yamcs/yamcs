
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  standalone: true,
  selector: 'app-significance-level',
  templateUrl: './significance-level.component.html',
  styleUrl: './significance-level.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
})
export class SignificanceLevelComponent {

  @Input()
  level: string;

  @Input()
  grayscale = false;
}
