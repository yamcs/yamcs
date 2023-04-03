import { AfterViewInit, ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { MatSort } from '@angular/material/sort';
import { Service } from '../../client';

@Component({
  selector: 'app-services-table',
  templateUrl: './ServicesTable.html',
  styleUrls: ['./ServicesTable.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesTable implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = ['state', 'name', 'className', 'failureMessage', 'actions'];

  @Input()
  dataSource = new MatLegacyTableDataSource<Service>();

  @Input()
  readonly = false;

  @Output()
  startService = new EventEmitter<string>();

  @Output()
  stopService = new EventEmitter<string>();

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
