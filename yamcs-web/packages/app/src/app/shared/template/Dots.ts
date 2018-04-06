import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

@Component({
  selector: 'app-dots',
  templateUrl: './Dots.html',
  styleUrls: ['./Dots.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class Dots {

  @Input()
  color: string;

  @Input()
  fontSize = '20px';
}
