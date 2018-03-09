import { Component, ChangeDetectionStrategy } from '@angular/core';
import { UserInfo } from '@yamcs/client';
import { YamcsService } from '../services/YamcsService';

@Component({
  templateUrl: './ProfilePage.html',
  styleUrls: ['./ProfilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {

  user$: Promise<UserInfo>;

  constructor(yamcs: YamcsService) {
    this.user$ = yamcs.yamcsClient.getUserInfo();
  }
}
