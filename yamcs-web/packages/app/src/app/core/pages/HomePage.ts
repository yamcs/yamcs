import { Component, ChangeDetectionStrategy, ViewChild, AfterViewInit } from '@angular/core';
import { Instance } from '@yamcs/client';
import { Title } from '@angular/platform-browser';
import { MatTableDataSource, MatSort, MatPaginator } from '@angular/material';
import { YamcsService } from '../services/YamcsService';

@Component({
  templateUrl: './HomePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  instances$: Promise<Instance[]>;

  dataSource = new MatTableDataSource<Instance>([]);

  displayedColumns = [
    'name',
  ];

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Yamcs');
    this.instances$ = yamcs.yamcsClient.getInstances();
    this.instances$.then(instances => {
      this.dataSource.data = instances;
    });

    this.dataSource.filterPredicate = (instance, filter) => {
      return instance.name.toLowerCase().indexOf(filter) >= 0;
    };
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }
}
