import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Stream, Instance } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './StreamPage.html',
  styleUrls: ['./StreamPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StreamPage implements OnInit {

  instance$: Observable<Instance>;
  stream$: Promise<Stream>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>, title: Title) {
    const name = route.snapshot.paramMap.get('name')!;
    title.setTitle(name + ' - Yamcs');
    this.stream$ = yamcs.getSelectedInstance().getStream(name);
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
