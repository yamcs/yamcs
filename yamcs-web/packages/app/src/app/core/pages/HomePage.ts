import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit } from '@angular/core';
import { Instance } from '@yamcs/client';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { selectInstances } from '../store/instance.selectors';
import { State } from '../../app.reducers';
import { Title } from '@angular/platform-browser';
import { MatTableDataSource, MatSort, MatPaginator } from '@angular/material';

@Component({
  templateUrl: './HomePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  instances$: Observable<Instance[]>;

  dataSource = new MatTableDataSource<Instance>([]);

  displayedColumns = [
    'name',
  ];

  constructor(private store: Store<State>, title: Title) {
    title.setTitle('Yamcs');
    this.instances$ = store.select(selectInstances);

    this.dataSource.filterPredicate = (instance, filter) => {
      return instance.name.toLowerCase().indexOf(filter) >= 0;
    };
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    this.store.select(selectInstances).subscribe(instances => {
      this.dataSource.data = instances;
    });
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }
}
