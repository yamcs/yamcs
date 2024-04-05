import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { Table, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  templateUrl: './table-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class TableListComponent implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = ['name', 'actions'];

  dataSource = new MatTableDataSource<Table>();

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
