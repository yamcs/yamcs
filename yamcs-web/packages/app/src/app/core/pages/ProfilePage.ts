import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { User } from '../../shared/User';
import { AuthService } from '../services/AuthService';

@Component({
  templateUrl: './ProfilePage.html',
  styleUrls: ['./ProfilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {

  user$: Observable<User | null>;

  constructor(authService: AuthService, title: Title) {
    title.setTitle('Profile');
    this.user$ = authService.user$;
  }
}
