import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ClientConnectionInfo, HttpTraffic, Synchronizer, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AdminPageTemplateComponent } from '../shared/admin-page-template/admin-page-template.component';
import { AdminToolbarComponent } from '../shared/admin-toolbar/admin-toolbar.component';
import { UserAgentPipe } from './user-agent.pipe';

@Component({
  standalone: true,
  templateUrl: './http-traffic.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdminPageTemplateComponent,
    AdminToolbarComponent,
    WebappSdkModule,
    UserAgentPipe,
  ],
})
export class HttpTrafficComponent implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  displayedColumns = [
    'id',
    'protocol',
    'remoteAddress',
    'readBytes',
    'writtenBytes',
    'readThroughput',
    'writeThroughput',
    'request',
    'authorization',
    'userAgent',
  ];

  tableTrackerFn = (index: number, conn: ClientConnectionInfo) => conn.id;

  traffic$ = new BehaviorSubject<HttpTraffic | null>(null);
  dataSource = new MatTableDataSource<ClientConnectionInfo>();

  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    title: Title,
    synchronizer: Synchronizer,
  ) {
    title.setTitle('HTTP traffic');

    this.refresh();
    this.syncSubscription = synchronizer.syncSlow(() => this.refresh());
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
  }

  private refresh() {
    this.yamcs.yamcsClient.getHttpTraffic().then(traffic => {
      this.traffic$.next(traffic);
      this.dataSource.data = traffic.connections || [];
    });
  }

  ngOnDestroy() {
    this.syncSubscription?.unsubscribe();
  }
}
