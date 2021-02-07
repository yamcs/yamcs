import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { PreferenceStore } from '../core/services/PreferenceStore';

@Component({
  templateUrl: './StoragePage.html',
  styleUrls: ['./StoragePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StoragePage {

  sidebar$: Observable<boolean>;

  constructor(
    preferenceStore: PreferenceStore,
  ) {
    this.sidebar$ = preferenceStore.sidebar$;
  }
}
