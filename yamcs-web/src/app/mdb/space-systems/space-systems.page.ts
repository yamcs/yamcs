import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';

import { Instance, SpaceSystem } from '../../../yamcs-client';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { State } from '../../app.reducers';
import { YamcsService } from '../../core/services/yamcs.service';
import { MatTableDataSource, MatSort } from '@angular/material';

@Component({
  templateUrl: './space-systems.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemsPageComponent implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['qualifiedName'];

  dataSource = new MatTableDataSource<SpaceSystem>();

  instance$: Observable<Instance>;

  constructor(yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);
    yamcs.getSelectedInstance().getSpaceSystems().subscribe(spaceSystems => {
      this.dataSource.data = spaceSystems;
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
