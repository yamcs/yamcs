import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Stream, YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

@Component({
  templateUrl: './streams.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamsPageComponent {

  streams$: Observable<Stream[]>;

  constructor(store: Store<State>, http: HttpClient) {
    this.streams$ = store.select(selectCurrentInstance).pipe(
      switchMap(instance => {
        const yamcs = new YamcsClient(http);
        return yamcs.getStreams(instance.name);
      }),
    );
  }
}
