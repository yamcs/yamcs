import { animate, state, style, transition, trigger } from '@angular/animations';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MessageService } from '../../core/services/MessageService';

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

  errorMessage$: Observable<string | null>;
  show$: Observable<boolean>;

  constructor(private messageService: MessageService) {
    this.errorMessage$ = messageService.errorMessage$;
    this.show$ = this.errorMessage$.pipe(
      map(msg => !!msg)
    );
  }

  dismiss() {
    this.messageService.dismiss();
  }
}
