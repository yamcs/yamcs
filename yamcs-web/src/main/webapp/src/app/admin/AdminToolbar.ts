import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Observable } from 'rxjs';
import { PreferenceStore } from '../core/services/PreferenceStore';

@Component({
  selector: 'app-admin-toolbar',
  templateUrl: './AdminToolbar.html',
  styleUrls: ['./AdminToolbar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminToolbar {

  @Input()
  hasDetailPane = false;

  showDetailPane$: Observable<boolean>;

  constructor(
    private preferenceStore: PreferenceStore,
  ) {
    this.showDetailPane$ = preferenceStore.detailPane$;
  }

  showDetailPane(enabled: boolean) {
    this.preferenceStore.setShowDetailPane(enabled);
  }
}
