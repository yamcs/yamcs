import { AsyncPipe } from '@angular/common';
import { Component } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatToolbar, MatToolbarRow } from '@angular/material/toolbar';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MessageService, SiteMessage } from '../../services/message.service';

@Component({
  selector: 'ya-message-bar',
  templateUrl: './message-bar.component.html',
  styleUrl: './message-bar.component.css',
  imports: [AsyncPipe, MatIcon, MatIconButton, MatToolbar, MatToolbarRow],
})
export class YaMessageBar {
  siteMessage$: Observable<SiteMessage | null>;
  show$: Observable<boolean>;

  constructor(private messageService: MessageService) {
    this.siteMessage$ = messageService.siteMessage$;
    this.show$ = this.siteMessage$.pipe(map((msg) => !!msg));
  }

  dismiss() {
    this.messageService.dismiss();
  }
}
