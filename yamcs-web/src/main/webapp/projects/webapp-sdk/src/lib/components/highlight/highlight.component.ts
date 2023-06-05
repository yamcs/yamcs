import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'ya-highlight',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './highlight.component.html',
})
export class HighlightComponent implements OnChanges {

  @Input()
  text: string;

  @Input()
  term: string;

  html$ = new BehaviorSubject<string>('');

  ngOnChanges() {
    if (!this.text || !this.term) {
      this.html$.next(this.text);
    } else {
      const re = new RegExp('(' + this.escapeRegex(this.term) + ')', 'ig');
      const html = this.text.replace(re, '<strong>$1</strong>');
      this.html$.next(html);
    }
  }

  private escapeRegex(pattern: string) {
    return pattern.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
  }
}
