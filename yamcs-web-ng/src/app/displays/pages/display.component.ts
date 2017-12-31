import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { YamcsClient } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';

import { map, switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';

@Component({
  templateUrl: './display.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayPageComponent {

  displayDefinition$: Observable<XMLDocument>;

  constructor(route: ActivatedRoute, store: Store<State>, http: HttpClient) {
    const name = route.snapshot.paramMap.get('name');
    if (name !== null) {
      this.displayDefinition$ = store.select(selectCurrentInstance).pipe(
        switchMap(instance => {
          const yamcs = new YamcsClient(http);
          return yamcs.getDisplay(instance.name, name);
        }),
        map(text => {
          const xmlParser = new DOMParser();
          return xmlParser.parseFromString(text, 'text/xml');
        }),
      );
    }
  }
}
