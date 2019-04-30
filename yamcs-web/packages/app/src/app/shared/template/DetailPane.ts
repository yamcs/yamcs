import { animate, style, transition, trigger } from '@angular/animations';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Observable } from 'rxjs';
import { PreferenceStore } from '../../core/services/PreferenceStore';

@Component({
  selector: 'app-detail-pane',
  templateUrl: './DetailPane.html',
  styleUrls: ['./DetailPane.css'],
  animations: [
    trigger('slideInOut', [
      transition(':enter', [
        style({ transform: 'translateX(0%)' }),
        animate('200ms ease-in', style({transform: 'translateX(0%)'}))
      ]),
      transition(':leave', [
        animate('200ms ease-in', style({transform: 'translateX(100%)'}))
      ])
    ])
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailPane {

  detailPane$: Observable<boolean>;

  constructor(preferenceStore: PreferenceStore) {
    this.detailPane$ = preferenceStore.detailPane$;
  }
}
