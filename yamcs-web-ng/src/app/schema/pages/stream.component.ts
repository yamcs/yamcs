import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Stream, YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

import * as utils from '../utils';

@Component({
  templateUrl: './stream.component.html',
  styleUrls: ['./streamsql.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamPageComponent {

  stream$: Observable<Stream>;

  constructor(route: ActivatedRoute, store: Store<State>, http: HttpClient) {
    const name = route.snapshot.paramMap.get('name');
    if (name != null) {
      this.stream$ = store.select(selectCurrentInstance).pipe(
        switchMap(instance => {
          const yamcs = new YamcsClient(http);
          return yamcs.getStream(instance.name, name);
        }),
      );
    }
  }

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
