import { animate, state, style, transition, trigger } from '@angular/animations';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MessageService, SiteMessage } from '../../services/message.service';

@Component({
  selector: 'ya-message-bar',
  templateUrl: './message-bar.component.html',
  styleUrls: ['./message-bar.component.css'],
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
