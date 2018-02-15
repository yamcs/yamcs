import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Stream, Instance } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import * as utils from '../utils';
import { YamcsService } from '../../core/services/yamcs.service';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './stream.component.html',
  styleUrls: [
    './stream.component.css',
    './streamsql.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamPageComponent implements OnInit {

  instance$: Observable<Instance>;
  stream$: Observable<Stream>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>) {
    const name = route.snapshot.paramMap.get('name');
    if (name != null) {
      this.stream$ = yamcs.getSelectedInstance().getStream(name);
    }
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }

  formatSQL(sql: string) {
    return utils.formatSQL(sql);
  }
}
