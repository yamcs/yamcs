import { AfterViewInit, ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Service, WebappSdkModule } from '@yamcs/webapp-sdk';
import { ServiceStateComponent } from '../service-state/service-state.component';

@Component({
  standalone: true,
  selector: 'app-services-table',
  templateUrl: './services-table.component.html',
  styleUrl: './services-table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ServiceStateComponent,
    WebappSdkModule,
  ],
})
export class ServicesTableComponent implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  displayedColumns = ['state', 'name', 'className', 'failureMessage', 'actions'];

  @Input()
  dataSource = new MatTableDataSource<Service>();

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
