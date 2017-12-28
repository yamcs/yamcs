import { Component, ChangeDetectionStrategy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';
import { filter, take } from 'rxjs/operators';

import { YamcsClient, Instance } from '../../../yamcs-client';
import { State } from '../../app.reducers';

@Component({
  templateUrl: './overview.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewPageComponent {

  instance$: Observable<Instance>;

  constructor(route: ActivatedRoute, http: HttpClient, store: Store<State>) {
    store.select(s => s.instances.selectedInstance)
      .pipe(
        filter(instance => instance !== null),
        take(1),
      ).subscribe(instance => {
        console.log('have2 ', instance);
        if (instance !== null) {
          const client = new YamcsClient(http);
          this.instance$ = client.getInstance(instance);
        }
      });
  }
}
