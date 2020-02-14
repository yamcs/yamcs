import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Significance } from '../../client';

@Component({
  selector: 'app-significance-level',
  templateUrl: './SignificanceLevel.html',
  styleUrls: ['./SignificanceLevel.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignificanceLevel {

  @Input()
  significance: Significance;

  @Input()
  grayscale = false;
}
