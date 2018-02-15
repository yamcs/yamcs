import { Component, ChangeDetectionStrategy } from '@angular/core';
import { UserInfo } from '../../../yamcs-client';
import { Observable } from 'rxjs/Observable';
import { YamcsService } from '../services/yamcs.service';

@Component({
  templateUrl: './profile.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileComponent {

  user$: Observable<UserInfo>;

  constructor(yamcs: YamcsService) {
    this.user$ = yamcs.yamcsClient.getUserInfo();
  }
}
