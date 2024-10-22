import { ChangeDetectionStrategy, Component, computed, inject, input, SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Component({
  standalone: true,
  selector: 'ya-highlight',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './highlight.component.html',
})
export class YaHighlight {

  private sanitizer = inject(DomSanitizer);

  text = input<string>();
  term = input<string>();

  html = computed(() => {
    const text = this.text();
    const term = this.term();
    if (!text || !term) {
      return text || '';
    } else {
      const re = new RegExp('(' + this.escapeRegex(term) + ')', 'ig');
      const html = text.replace(re, '<strong>$1</strong>');
      const safeHtml = this.sanitizer.sanitize(SecurityContext.HTML, html);
      return safeHtml || '';
    }
  });

  private escapeRegex(pattern: string) {
    return pattern.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
  }
}
