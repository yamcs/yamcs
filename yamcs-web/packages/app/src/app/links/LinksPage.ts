import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Link, LinkEvent } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { AuthService } from '../core/services/AuthService';
import { YamcsService } from '../core/services/YamcsService';



@Component({
  templateUrl: './LinksPage.html',
  styleUrls: ['./LinksPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinksPage implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = ['status', 'name', 'className', 'args', 'in', 'out', 'actions'];

  dataSource = new MatTableDataSource<Link>();

  linkSubscription: Subscription;

  private linksByName: { [key: string]: Link } = {};

  constructor(private yamcs: YamcsService, private authService: AuthService, title: Title, private changeDetection: ChangeDetectorRef) {
    title.setTitle('Links - Yamcs');

    // Fetch with REST first, otherwise may take up to a second
    // before we get an update via websocket.
    this.yamcs.getInstanceClient()!.getLinks().then(links => {
      for (const link of links) {
        this.linksByName[link.name] = link;
      }
      this.dataSource.data = Object.values(this.linksByName);
    });

    this.yamcs.getInstanceClient()!.getLinkUpdates().then(response => {
      this.linkSubscription = response.linkEvent$.subscribe(evt => {
        this.processLinkEvent(evt);
      });
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  // trackBy is needed to prevent menu from closing when
  // the link is updated.
  tableTrackerFn = (index: number, link: Link) => link.name;

  enableLink(name: string) {
    this.yamcs.getInstanceClient()!.enableLink(name);
  }

  disableLink(name: string) {
    this.yamcs.getInstanceClient()!.disableLink(name);
  }

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
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

    // Needed to show table updates in combination with trackBy
    this.changeDetection.detectChanges();
  }

  ngOnDestroy() {
    if (this.linkSubscription) {
      this.linkSubscription.unsubscribe();
    }
  }
}
