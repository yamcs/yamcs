import { ChangeDetectionStrategy, Component, effect, ElementRef, input, SecurityContext, viewChild } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { marked } from 'marked';

@Component({
  standalone: true,
  selector: 'app-markdown',
  templateUrl: './markdown.component.html',
  styleUrl: './markdown.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarkdownComponent {

  text = input.required<string>();
  ref = viewChild<ElementRef<HTMLDivElement>>('md');

  constructor(sanitizer: DomSanitizer) {
    effect(() => {
      const ref = this.ref();
      if (ref) {
        const text = this.text();
        let html = text ? marked.parse(text) : '';
        html = sanitizer.sanitize(SecurityContext.HTML, html)!;
        ref.nativeElement.innerHTML = html;
      }
    });
  }
}
