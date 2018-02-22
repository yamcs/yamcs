import { Component, ChangeDetectionStrategy } from '@angular/core';
import { UserInfo } from '../../../yamcs-client';
import { Observable } from 'rxjs/Observable';
import { YamcsService } from '../services/YamcsService';

@Component({
  templateUrl: './ProfilePage.html',
  styleUrls: ['./ProfilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {

  user$: Observable<UserInfo>;

  constructor(yamcs: YamcsService) {
    this.user$ = yamcs.yamcsClient.getUserInfo();
  }
}
