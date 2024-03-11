import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'ya-dots',
  templateUrl: './dots.component.html',
  styleUrl: './dots.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DotsComponent {

  @Input()
  color: string;

  @Input()
  fontSize = '20px';
}
