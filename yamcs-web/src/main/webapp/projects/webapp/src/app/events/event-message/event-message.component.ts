import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-event-message',
  templateUrl: './event-message.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class EventMessageComponent implements OnInit {

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
