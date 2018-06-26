import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { User } from '../../shared/User';

@Component({
  templateUrl: './ProfilePage.html',
  styleUrls: ['./ProfilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {

  user$: Observable<User | null>;

  constructor(authService: AuthService, title: Title) {
    title.setTitle('Profile - Yamcs');
    this.user$ = authService.user$;
  }
}
