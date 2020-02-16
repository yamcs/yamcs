import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-significance-level',
  templateUrl: './SignificanceLevel.html',
  styleUrls: ['./SignificanceLevel.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignificanceLevel {

  @Input()
  level: string;

  @Input()
  grayscale = false;
}
