import { Component, ElementRef, ViewChild } from '@angular/core';

@Component({
  standalone: true,
  template: '<div #tt class="ya-tooltip"></div>',
  styleUrl: './timeline-tooltip.component.css',
})
export class TimelineTooltipComponent {

  @ViewChild('tt', { static: true })
  tt: ElementRef<HTMLDivElement>;

  show(text: string, left: number, top: number) {
    const el = this.tt.nativeElement;
    el.style.left = left + 'px';
    el.style.top = top + 'px';
    el.style.display = 'block';
    el.innerText = text;
  }

  hide() {
    const el = this.tt.nativeElement;
    el.style.display = 'none';
  }
}
