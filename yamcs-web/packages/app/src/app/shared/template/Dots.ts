import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-dots',
  template: '<span class="dot1">&#9642;</span><span class="dot2">&#9642;</span><span class="dot3">&#9642;</span>',
  styleUrls: ['./Dots.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class Dots {
}
