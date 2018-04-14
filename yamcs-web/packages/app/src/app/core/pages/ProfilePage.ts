import { Component, ChangeDetectionStrategy } from '@angular/core';
import { UserInfo } from '@yamcs/client';
import { Title } from '@angular/platform-browser';
import { AuthService } from '../services/AuthService';
import { Observable } from 'rxjs/Observable';

@Component({
  templateUrl: './ProfilePage.html',
  styleUrls: ['./ProfilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {

  user$: Observable<UserInfo | null>;

  constructor(authService: AuthService, title: Title) {
    title.setTitle('Profile - Yamcs');
    this.user$ = authService.userInfo$;
  }
}
