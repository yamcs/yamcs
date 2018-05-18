import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { UserInfo } from '@yamcs/client';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';

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
