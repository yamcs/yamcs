import { animate, state, style, transition, trigger } from '@angular/animations';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MessageService, SiteMessage } from '../../core/services/MessageService';

@Component({
  selector: 'app-message-bar',
  templateUrl: './MessageBar.html',
  styleUrls: ['./MessageBar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [
    trigger('openClose', [
      state('in', style({ transform: 'translateY(0)' })),
      transition(':enter', [
        style({ transform: 'translateY(-100%)' }),
        animate('0.35s ease')
      ]),
    ]),
  ],
})
export class MessageBar {

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
