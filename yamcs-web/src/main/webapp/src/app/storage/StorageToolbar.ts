import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { Observable } from 'rxjs';
import { PreferenceStore } from '../core/services/PreferenceStore';

@Component({
  selector: 'app-storage-toolbar',
  templateUrl: './StorageToolbar.html',
  styleUrls: ['./StorageToolbar.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StorageToolbar {

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
