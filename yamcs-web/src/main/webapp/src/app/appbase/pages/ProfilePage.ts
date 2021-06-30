import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { ConfigService, WebsiteConfig } from '../../core/services/ConfigService';
import { User } from '../../shared/User';

@Component({
  templateUrl: './ProfilePage.html',
  styleUrls: ['./ProfilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {

  user$: Observable<User | null>;
  config: WebsiteConfig;

  constructor(authService: AuthService, title: Title, configService: ConfigService) {
    title.setTitle('Profile');
    this.user$ = authService.user$;
    this.config = configService.getConfig();
  }
}
