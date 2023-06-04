import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Component({
  selector: 'app-event-message',
  templateUrl: './EventMessage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventMessage implements OnInit {

  @Input()
  message?: string;

  @Input()
  highlight?: string;

  summary?: string;
  detail?: string;
  expanded$ = new BehaviorSubject<boolean>(false);

  ngOnInit() {
    if (this.message !== null && this.message !== undefined) {
      const idx = this.message.indexOf('\n');
      if (idx === -1) {
        this.summary = this.message;
      } else {
        this.summary = this.message.substring(0, idx);
        if (idx + 1 < this.message.length) {
          this.detail = this.message.substring(idx + 1);
        }
      }
    }
  }

  toggleExpanded() {
    this.expanded$.next(!this.expanded$.value);
  }
}
