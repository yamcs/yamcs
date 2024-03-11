import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MessageService, SiteMessage } from '../../services/message.service';

@Component({
  selector: 'ya-message-bar',
  templateUrl: './message-bar.component.html',
  styleUrl: './message-bar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageBarComponent {

  siteMessage$: Observable<SiteMessage | null>;
  show$: Observable<boolean>;

  constructor(private messageService: MessageService) {
    this.siteMessage$ = messageService.siteMessage$;
    this.show$ = this.siteMessage$.pipe(
      map(msg => !!msg)
    );
  }

  dismiss() {
    this.messageService.dismiss();
  }
}
