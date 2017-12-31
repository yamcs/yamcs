import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { YamcsClient, DisplayInfo } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

@Component({
  templateUrl: './displays.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplaysPageComponent {

  displayInfo$: Observable<DisplayInfo>;

  constructor(store: Store<State>, http: HttpClient) {
    this.displayInfo$ = store.select(selectCurrentInstance).pipe(
      switchMap(instance => {
        const yamcs = new YamcsClient(http);
        return yamcs.getDisplayInfo(instance.name);
      }),
    );
  }
}
