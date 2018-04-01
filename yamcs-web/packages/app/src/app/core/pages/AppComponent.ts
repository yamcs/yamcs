import { Component, ChangeDetectionStrategy, HostBinding } from '@angular/core';
import { Instance, UserInfo } from '@yamcs/client';
import { YamcsService } from '../services/YamcsService';
import { MatDialog } from '@angular/material';
import { SelectInstanceDialog } from '../../shared/template/SelectInstanceDialog';
import { Observable } from 'rxjs/Observable';
import { OverlayContainer } from '@angular/cdk/overlay';
import { PreferenceStore } from '../services/PreferenceStore';

@Component({
  selector: 'app-root',
  templateUrl: './AppComponent.html',
  styleUrls: ['./AppComponent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {

  @HostBinding('class')
  componentCssClass: string;

  title = 'Yamcs';

  instance$: Observable<Instance | null>;
  user$: Promise<UserInfo>;

  darkMode$: Observable<boolean>;

  constructor(
    yamcs: YamcsService,
    private preferenceStore: PreferenceStore,
    private dialog: MatDialog,
    private overlayContainer: OverlayContainer,
  ) {
    this.instance$ = yamcs.instance$;
    this.user$ = yamcs.yamcsClient.getUserInfo();

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

  private disableDarkMode() {
    this.overlayContainer.getContainerElement().classList.remove('dark-theme');
    this.componentCssClass = '';
    this.preferenceStore.setDarkMode(false);
  }

  private enableDarkMode() {
    this.overlayContainer.getContainerElement().classList.add('dark-theme');
    this.componentCssClass = 'dark-theme';
    this.preferenceStore.setDarkMode(true);
  }
}
