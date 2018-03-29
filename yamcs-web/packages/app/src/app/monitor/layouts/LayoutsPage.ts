import { Component, ChangeDetectionStrategy } from '@angular/core';

import { MatTableDataSource } from '@angular/material';
import { Instance } from '@yamcs/client';
import { Observable } from 'rxjs/Observable';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { NamedLayout, LayoutStorage } from '../displays/LayoutStorage';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './LayoutsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LayoutsPage {

  instance$: Observable<Instance>;

  displayedColumns = ['name'];
  dataSource = new MatTableDataSource<NamedLayout>([]);

  constructor(store: Store<State>, title: Title) {
    title.setTitle('Layouts - Yamcs');
    this.instance$ = store.select(selectCurrentInstance);
    this.instance$.subscribe(instance => {
      const layouts = LayoutStorage.getLayouts(instance.name);
      this.dataSource.data = layouts;
    });
  }
}
