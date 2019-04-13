import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { User } from '../../shared/User';

@Component({
  selector: 'app-main-page',
  templateUrl: './MainPage.html',
  styleUrls: ['./MainPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MainPage {

  private user: User;
  sidebar$: Observable<boolean>;

  constructor(authService: AuthService, preferenceStore: PreferenceStore) {
    this.user = authService.getUser()!;
    this.sidebar$ = preferenceStore.sidebar$;
  }

  showServicesItem() {
    return this.user.hasSystemPrivilege('ControlServices');
  }
}
