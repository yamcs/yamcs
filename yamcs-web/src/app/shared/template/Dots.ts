import { Component, ChangeDetectionStrategy } from '@angular/core'

@Component({
  selector: 'app-dots',
  template: '<span class="dot1">&#9642;</span><span class="dot2">&#9642;</span><span class="dot3">&#9642;</span>',
  styles: [`
    @keyframes blink {
      50% {
        color: transparent;
      }
    }
    .dot1, .dot2, .dot3 {
      font-size: 20px;
      animation: 1s blink infinite;
    }
    .dot2 {
      animation-delay: 250ms;
    }
    .dot3 {
      animation-delay: 500ms;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class Dots {
}
