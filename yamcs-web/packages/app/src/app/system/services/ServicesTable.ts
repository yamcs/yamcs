import { AfterViewInit, ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { MatSort, MatTableDataSource } from '@angular/material';
import { Service } from '@yamcs/client';



@Component({
  selector: 'app-services-table',
  templateUrl: './ServicesTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesTable implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['state', 'name', 'className', 'actions'];

  @Input()
  dataSource = new MatTableDataSource<Service>();

  @Output()
  startService = new EventEmitter<string>();

  @Output()
  stopService = new EventEmitter<string>();

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
