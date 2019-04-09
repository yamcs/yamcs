import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AuthService } from '../../core/services/AuthService';
import { User } from '../../shared/User';

@Component({
  selector: 'app-main-page',
  templateUrl: './MainPage.html',
  styleUrls: ['./MainPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MainPage {

  private user: User;

  constructor(authService: AuthService) {
    this.user = authService.getUser()!;
  }

  showServicesItem() {
    return this.user.hasSystemPrivilege('ControlServices');
  }
}
