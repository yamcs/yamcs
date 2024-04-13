import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  standalone: true,
  selector: 'ya-dots',
  templateUrl: './dots.component.html',
  styleUrl: './dots.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaDots {

  @Input()
  color: string;

  @Input()
  fontSize = '20px';
}
