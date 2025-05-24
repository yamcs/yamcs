import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  SecurityContext,
  ViewChild,
} from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Component({
  template: '<div #tt class="ya-tooltip"></div>',
  styleUrl: './tooltip.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaTooltip {
  html = input(false);

  @ViewChild('tt', { static: true })
  tt: ElementRef<HTMLDivElement>;

  constructor(private sanitizer: DomSanitizer) {}

  show(text: string, left: number, top: number) {
    const el = this.tt.nativeElement;
    el.style.left = left + 'px';
    el.style.top = top + 'px';
    el.style.display = 'block';
    if (this.html()) {
      const safeHtml = this.sanitizer.sanitize(SecurityContext.HTML, text);
      el.innerHTML = safeHtml ?? '';
    } else {
      el.innerText = text;
    }
  }

  hide() {
    const el = this.tt.nativeElement;
    el.style.display = 'none';
  }
}
