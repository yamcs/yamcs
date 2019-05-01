import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { PreferenceStore } from '../core/services/PreferenceStore';

@Component({
  templateUrl: './AdminPage.html',
  styleUrls: ['./AdminPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminPage {

  sidebar$: Observable<boolean>;

  constructor(
    preferenceStore: PreferenceStore,
  ) {
    this.sidebar$ = preferenceStore.sidebar$;
  }
}
