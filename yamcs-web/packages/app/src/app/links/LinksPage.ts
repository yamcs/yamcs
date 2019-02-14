import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Link, LinkEvent } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AuthService } from '../core/services/AuthService';
import { YamcsService } from '../core/services/YamcsService';
import { LinkItem } from './LinkItem';

@Component({
  templateUrl: './LinksPage.html',
  styleUrls: ['./LinksPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinksPage implements OnDestroy {

  selectedItem$ = new BehaviorSubject<LinkItem | null>(null);

  displayedColumns = ['status', 'name', 'className', 'in', 'out', 'actions'];

  dataSource = new MatTableDataSource<LinkItem>();

  linkSubscription: Subscription;

  private itemsByName: { [key: string]: LinkItem } = {};

  constructor(private yamcs: YamcsService, private authService: AuthService, title: Title, private changeDetection: ChangeDetectorRef) {
    title.setTitle('Links - Yamcs');

    // Fetch with REST first, otherwise may take up to a second
    // before we get an update via websocket.
    this.yamcs.getInstanceClient()!.getLinks().then(links => {
      for (const link of links) {
        const linkItem = { link, hasChildren: false, expanded: false };
        this.itemsByName[link.name] = linkItem;
      }
      for (const link of links) { // 2nd pass
        if (link.parentName) {
          const parent = this.itemsByName[link.parentName];
          parent.hasChildren = true;
          this.itemsByName[link.name].parentLink = parent.link;
        }
      }
      this.updateDataSource();

      this.yamcs.getInstanceClient()!.getLinkUpdates().then(response => {
        this.linkSubscription = response.linkEvent$.subscribe(evt => {
          this.processLinkEvent(evt);
        });
      });
    });

    this.dataSource.filterPredicate = (item, filter) => {
      return item.link.name.toLowerCase().indexOf(filter) >= 0
        || item.link.type.toLowerCase().indexOf(filter) >= 0;
    };
  }

  // trackBy is needed to prevent menu from closing when
  // the link is updated.
  tableTrackerFn = (index: number, link: Link) => link.name;

  expandItem(item: LinkItem) {
    item.expanded = !item.expanded;
    this.updateDataSource();
  }

  enableLink(name: string) {
    this.yamcs.getInstanceClient()!.enableLink(name);
  }

  disableLink(name: string) {
    this.yamcs.getInstanceClient()!.disableLink(name);
  }

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }

  private processLinkEvent(evt: LinkEvent) {
    // Trigger change detection for detail pane etc
    const selectedItem = this.selectedItem$.value;
    if (selectedItem && selectedItem.link.name === evt.linkInfo.name) {
      selectedItem.link = evt.linkInfo;
      for (const subitem of Object.values(this.itemsByName)) {
        if (subitem.link.parentName === evt.linkInfo.name) {
          subitem.parentLink = evt.linkInfo;
        }
      }
      this.selectedItem$.next({ ...selectedItem });
    }

    switch (evt.type) {
      case 'REGISTERED':
      case 'UPDATED':
        this.itemsByName[evt.linkInfo.name].link = evt.linkInfo;
        this.updateDataSource();
        break;
      case 'UNREGISTERED':
        delete this.itemsByName[evt.linkInfo.name];
        this.updateDataSource();
        break;
      default:
        console.error('Unexpected link update of type ' + evt.type);
        break;
    }

    // Needed to show table updates in combination with trackBy
    this.changeDetection.detectChanges();
  }

  private updateDataSource() {
    const data = Object.values(this.itemsByName).filter(item => {
      const parentName = item.link.parentName;
      if (!parentName) {
        return true;
      } else {
        const parent = this.itemsByName[parentName];
        return parent.expanded;
      }
    });
    data.sort((x, y) => {
      return x.link.name.localeCompare(y.link.name);
    });
    this.dataSource.data = data;
  }

  selectLink(item: LinkItem) {
    this.selectedItem$.next(item);
  }

  ngOnDestroy() {
    if (this.linkSubscription) {
      this.linkSubscription.unsubscribe();
    }
  }
}
