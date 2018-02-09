import { Component, ChangeDetectionStrategy, AfterViewInit, ViewChild } from '@angular/core';

import { Link, LinkEvent } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';
import { MatTableDataSource, MatSort } from '@angular/material';

@Component({
  templateUrl: './links.component.html',
  styleUrls: ['./links.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinksPageComponent implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['status', 'name', 'type', 'spec', 'stream', 'dataCount', 'actions'];

  dataSource = new MatTableDataSource<Link>();

  private linksByName: { [key: string]: Link } = {};

  constructor(private yamcs: YamcsService) {
    this.yamcs.getSelectedInstance().getLinkUpdates().subscribe(evt => {
      this.processLinkEvent(evt);
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  /*
   * TODO would like to pass this via [trackBy] of mat-table
   * so that rows are recycled. But this causes update issues.
   *
   * This link suggests that it should work though:
   * https://github.com/angular/material2/issues/7877
   *
   * Maybe use custom DataSource?
   */
  tableTrackerFn = (index: number, link: Link) => link.name;

  enableLink(name: string) {
    this.yamcs.getSelectedInstance().enableLink(name).subscribe();
  }

  disableLink(name: string) {
    this.yamcs.getSelectedInstance().disableLink(name).subscribe();
  }

  private processLinkEvent(evt: LinkEvent) {
    switch (evt.type) {
      case 'REGISTERED':
      case 'UPDATED':
        this.linksByName[evt.linkInfo.name] = evt.linkInfo;
        this.dataSource.data = Object.values(this.linksByName);
        break;
      case 'UNREGISTERED':
        delete this.linksByName[evt.linkInfo.name];
        this.dataSource.data = Object.values(this.linksByName);
        break;
      default:
        console.error('Unexpected link update of type ' + evt.type);
        break;
    }
  }
}
