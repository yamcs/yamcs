import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Service, YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

@Component({
  templateUrl: './services.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesPageComponent {

  services$: Observable<Service[]>;

  constructor(store: Store<State>, http: HttpClient) {
    this.services$ = store.select(selectCurrentInstance).pipe(
      switchMap(instance => {
        const yamcs = new YamcsClient(http);
        return yamcs.getServices(instance.name);
      }),
    );
  }
}
