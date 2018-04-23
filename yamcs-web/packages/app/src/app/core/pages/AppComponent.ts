import { Component, ChangeDetectionStrategy, HostBinding, OnDestroy } from '@angular/core';
import { Instance, UserInfo } from '@yamcs/client';
import { YamcsService } from '../services/YamcsService';
import { MatDialog } from '@angular/material';
import { SelectInstanceDialog } from '../../shared/template/SelectInstanceDialog';
import { Observable } from 'rxjs/Observable';
import { PreferenceStore } from '../services/PreferenceStore';
import { AuthService } from '../services/AuthService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Subscription } from 'rxjs/Subscription';

@Component({
  selector: 'app-root',
  templateUrl: './AppComponent.html',
  styleUrls: ['./AppComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent implements OnDestroy {

  @HostBinding('class')
  componentCssClass: string;

  title = 'Yamcs';

  instance$: Observable<Instance | null>;
  user$: Observable<UserInfo | null>;

  darkMode$: Observable<boolean>;
  showMdbItem$ = new BehaviorSubject<boolean>(false);

  userSubscription: Subscription;

  constructor(
    yamcs: YamcsService,
    private authService: AuthService,
    private preferenceStore: PreferenceStore,
    private dialog: MatDialog,
  ) {
    this.instance$ = yamcs.instance$;
    this.user$ = authService.userInfo$;

    this.userSubscription = this.user$.subscribe(user => {
      this.showMdbItem$.next(authService.hasSystemPrivilege('MayGetMissionDatabase'));
    });

    this.darkMode$ = preferenceStore.darkMode$;
    if (preferenceStore.isDarkMode()) {
      this.enableDarkMode();
    }
  }

  openInstanceDialog() {
    this.dialog.open(SelectInstanceDialog, {
      width: '600px',
      autoFocus: false,
    });
  }

  toggleDarkTheme() {
    if (this.preferenceStore.isDarkMode()) {
      this.disableDarkMode();
    } else {
      this.enableDarkMode();
    }
  }

  logout() {
    this.authService.logout();
  }

  private enableDarkMode() {
    document.body.classList.add('dark-theme');
    this.preferenceStore.setDarkMode(true);
  }

  private disableDarkMode() {
    document.body.classList.remove('dark-theme');
    this.preferenceStore.setDarkMode(false);
  }

  ngOnDestroy() {
    if (this.userSubscription) {
      this.userSubscription.unsubscribe();
    }
  }
}
