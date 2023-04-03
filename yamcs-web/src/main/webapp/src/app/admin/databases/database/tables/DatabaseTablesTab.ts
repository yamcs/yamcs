import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { MatSort } from '@angular/material/sort';
import { ActivatedRoute } from '@angular/router';
import { Table } from '../../../../client';
import { YamcsService } from '../../../../core/services/YamcsService';

@Component({
  templateUrl: './DatabaseTablesTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatabaseTablesTab implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = ['name', 'actions'];

  dataSource = new MatLegacyTableDataSource<Table>();

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService) {
    const parent = route.snapshot.parent!;
    const name = parent.paramMap.get('database')!;
    yamcs.yamcsClient.getTables(name).then(tables => {
      this.dataSource.data = tables;
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
