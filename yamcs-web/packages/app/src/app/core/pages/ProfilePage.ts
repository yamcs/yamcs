import { Component, ChangeDetectionStrategy } from '@angular/core';
import { UserInfo } from '@yamcs/client';
import { YamcsService } from '../services/YamcsService';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './ProfilePage.html',
  styleUrls: ['./ProfilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {

  user$: Promise<UserInfo>;

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Profile - Yamcs');
    this.user$ = yamcs.yamcsClient.getUserInfo();
  }
}
