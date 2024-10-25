import { ChangeDetectionStrategy, Component, computed, inject, input, SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

/**
 * Highlights action log entries.
 */
@Component({
  standalone: true,
  selector: './ya-action-log-summary',
  templateUrl: './action-log-summary.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaActionLogSummary {

  sanitizer = inject(DomSanitizer);

  text = input<string>();

  html = computed(() => {
    const text = this.text();

    if (!text) {
      return null;
    }

    const html = text.replace(/(\'[^\']+\')/g, '<strong>\$1</strong>');
    return this.sanitizer.sanitize(SecurityContext.HTML, html);
  });
}
