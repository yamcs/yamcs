import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { PreferenceStore } from '../../core/services/PreferenceStore';

@Component({
  selector: 'app-detail-pane',
  templateUrl: './DetailPane.html',
  styleUrls: ['./DetailPane.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailPane {

  detailPane$: Observable<boolean>;

  constructor(preferenceStore: PreferenceStore) {
    this.detailPane$ = preferenceStore.detailPane$;
  }
}
