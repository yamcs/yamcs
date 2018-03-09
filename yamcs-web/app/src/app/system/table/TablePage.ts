import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Table, Instance } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './TablePage.html',
  styleUrls: ['./TablePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablePage implements OnInit {

  instance$: Observable<Instance>;
  table$: Promise<Table>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>) {
    const name = route.snapshot.paramMap.get('name')!;
    this.table$ = yamcs.getSelectedInstance().getTable(name);
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
