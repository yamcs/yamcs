import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';

import { YamcsService } from '../../core/services/YamcsService';
import { SavedLayout } from '../displays/SavedLayout';
import { MatTableDataSource } from '@angular/material';
import { Instance } from '../../../yamcs-client';
import { Observable } from 'rxjs/Observable';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './LayoutsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutsPage implements OnInit {

  instance$: Observable<Instance>;

  displayedColumns = ['name'];
  dataSource = new MatTableDataSource<SavedLayout>([]);

  constructor(yamcs: YamcsService, private store: Store<State>) {
    const instance = yamcs.getSelectedInstance().instance;
    const item = localStorage.getItem(`yamcs.${instance}.savedLayouts`);
    if (item) {
      const savedLayouts = JSON.parse(item) as SavedLayout[];
      this.dataSource.data = savedLayouts;
    }
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
