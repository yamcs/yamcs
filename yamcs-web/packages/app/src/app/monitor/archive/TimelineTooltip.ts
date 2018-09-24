import { Component, ElementRef, ViewChild } from '@angular/core';

@Component({
  template: '<div #tt class="ya-tooltip"></div>',
  styleUrls: ['./TimelineTooltip.css'],
})
export class TimelineTooltip {

  @ViewChild('tt')
  tt: ElementRef;

  show(text: string, left: number, top: number) {
    const el = this.tt.nativeElement;
    el.style.left = left + 'px';
    el.style.top = top + 'px';
    el.style.display = 'block';
    el.innerHTML = text;
  }

  hide() {
    const el = this.tt.nativeElement;
    el.style.display = 'none';
  }
}
