import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { ActionInfo, LinkEvent, LinkSubscription, MessageService, WebappSdkModule, YaColumnInfo, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { LinkDetailComponent } from '../link-detail/link-detail.component';
import { LinkStatusComponent } from '../link-status/link-status.component';
import { LinksPageTabsComponent } from '../links-page-tabs/links-page-tabs.component';
import { LinkService } from '../shared/link.service';
import { LinkItem } from './model';

@Component({
  standalone: true,
  templateUrl: './link-list.component.html',
  styleUrl: './link-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    LinkDetailComponent,
    LinkStatusComponent,
    LinksPageTabsComponent,
    WebappSdkModule,
  ],
})
export class LinkListComponent implements AfterViewInit, OnDestroy {

  filterControl = new UntypedFormControl();

  // Link to show detail pane (only on single selection)
  detailLink$ = new BehaviorSubject<LinkItem | null>(null);

  columns: YaColumnInfo[] = [
    { id: 'select', label: '', alwaysVisible: true },
    { id: 'status', label: '', alwaysVisible: true },
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'className', label: 'Class name' },
    { id: 'in', label: 'In count', visible: true },
    { id: 'out', label: 'Out count', visible: true },
    { id: 'detailedStatus', label: 'Detail', visible: true },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  dataSource = new MatTableDataSource<LinkItem>();
  selection = new SelectionModel<LinkItem>(true, []);

  private selectionSubscription: Subscription;
  private linkSubscription: LinkSubscription;

  private itemsByName: { [key: string]: LinkItem; } = {};

  constructor(
    readonly yamcs: YamcsService,
    private authService: AuthService,
    title: Title,
    private changeDetection: ChangeDetectorRef,
    private route: ActivatedRoute,
    private router: Router,
    private messageService: MessageService,
    private linkService: LinkService,
  ) {
    title.setTitle('Links');

    this.dataSource.filterPredicate = (item, filter) => {
      return item.link.name.toLowerCase().indexOf(filter) >= 0
        || item.link.type.toLowerCase().indexOf(filter) >= 0;
    };

    this.selectionSubscription = this.selection.changed.subscribe(() => {
      const selected = this.selection.selected;
      if (selected.length === 1) {
        this.detailLink$.next(selected[0]);
      } else {
        this.detailLink$.next(null);
      }
    });
  }

  ngAfterViewInit() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filterControl.setValue(queryParams.get('filter'));
      this.dataSource.filter = queryParams.get('filter')!.toLowerCase();
    }

    this.filterControl.valueChanges.subscribe(() => {
      this.updateURL();
      const value = this.filterControl.value || '';
      this.dataSource.filter = value.toLowerCase();

      for (const item of this.selection.selected) {
        if (this.dataSource.filteredData.indexOf(item) === -1) {
          this.selection.deselect(item);
        }
      }
    });

    // Fetch with REST first, otherwise may take up to a second
    // before we get an update via websocket.
    this.yamcs.yamcsClient.getLinks(this.yamcs.instance!).then(links => {
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

      this.linkSubscription = this.yamcs.yamcsClient.createLinkSubscription({
        instance: this.yamcs.instance!,
      }, evt => {
        this.processLinkEvent(evt);
      });
    });
  }

  // trackBy is needed to prevent menu from closing when
  // the link is updated.
  tableTrackerFn = (index: number, item: LinkItem) => item.link.name;

  expandItem($event: Event, item: LinkItem) {
    item.expanded = !item.expanded;

    // Unselect child links when parent is collapsed
    if (!item.expanded) {
      for (const selectedItem of this.selection.selected) {
        if (selectedItem.parentLink && selectedItem.parentLink.name === item.link.name) {
          this.selection.deselect(selectedItem);
        }
      }
    }

    this.updateDataSource();

    // Prevent row selection
    $event.stopPropagation();
  }

  enableLink(link: string) {
    this.yamcs.yamcsClient.enableLink(this.yamcs.instance!, link)
      .catch(err => this.messageService.showError(err));
  }

  disableLink(link: string) {
    this.yamcs.yamcsClient.disableLink(this.yamcs.instance!, link)
      .catch(err => this.messageService.showError(err));
  }

  resetCounters(link: string) {
    this.yamcs.yamcsClient.resetLinkCounters(this.yamcs.instance!, link)
      .catch(err => this.messageService.showError(err));
  }

  runAction(link: string, action: ActionInfo) {
    this.linkService.runAction(link, action);
  }

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
  }

  private processLinkEvent(evt: LinkEvent) {
    const linkNames: string[] = [];
    for (const linkInfo of evt.links || []) {
      linkNames.push(linkInfo.name);

      // Update detail pane
      const selectedItem = this.detailLink$.value;
      if (selectedItem && selectedItem.link.name === linkInfo.name) {
        selectedItem.link = linkInfo;
        for (const subitem of Object.values(this.itemsByName)) {
          if (subitem.link.parentName === linkInfo.name) {
            subitem.parentLink = linkInfo;
          }
        }
        this.detailLink$.next({ ...selectedItem });
      }

      if (linkInfo.name in this.itemsByName) {
        this.itemsByName[linkInfo.name].link = linkInfo;
      } else {
        const linkItem = { link: linkInfo, hasChildren: false, expanded: false };
        this.itemsByName[linkInfo.name] = linkItem;
      }

      for (const item of Object.values(this.itemsByName)) {
        if (item.parentLink && item.parentLink.name === linkInfo.name) {
          item.parentLink = linkInfo;
        }
        if (linkInfo.parentName && linkInfo.parentName === item.link.name) {
          item.hasChildren = true;
        }
      }
    }

    const toBeDeleted: string[] = [];
    for (const itemName in this.itemsByName) {
      if (linkNames.indexOf(itemName) === -1) {
        const item = this.itemsByName[itemName];
        this.selection.deselect(item);
        toBeDeleted.push(itemName);
      }
    }
    for (const itemName of toBeDeleted) {
      delete this.itemsByName[itemName];
    }

    this.updateDataSource();

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
      const xParts = x.link.name.split('.');
      const yParts = y.link.name.split('.');
      for (let i = 0; i < Math.max(xParts.length, yParts.length); i++) {
        const xPart = i < xParts.length ? xParts[i] : '';
        const yPart = i < yParts.length ? yParts[i] : '';
        const cmp = xPart.localeCompare(yPart);
        if (cmp !== 0) {
          return cmp;
        }
      }
      return 0;
    });
    this.dataSource.data = data;
  }

  allowGroupEnable() {
    // Allow if at least one of the selected links is disabled
    for (const item of this.selection.selected) {
      if (item.link.disabled) {
        return true;
      }
    }
    return false;
  }

  allowGroupDisable() {
    // Allow if at least one of the selected links is enabled
    for (const item of this.selection.selected) {
      if (!item.link.disabled) {
        return true;
      }
    }
    return false;
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.filteredData.length;
    return numSelected === numRows && numRows > 0;
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.filteredData.forEach(row => this.selection.select(row));
  }

  toggleOne(row: LinkItem) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  enableSelectedLinks() {
    for (const item of this.selection.selected) {
      this.enableLink(item.link.name);
    }
  }

  disableSelectedLinks() {
    for (const item of this.selection.selected) {
      this.disableLink(item.link.name);
    }
  }

  resetCountersForSelectedLinks() {
    for (const item of this.selection.selected) {
      this.resetCounters(item.link.name);
    }
  }

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  selectNext() {
    const items = this.dataSource.filteredData;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[this.selection.selected.length - 1];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.min(items.indexOf(currentItem) + 1, items.length - 1);
      }
    }
    this.selection.clear();
    this.selection.select(items[idx]);
  }

  selectPrevious() {
    const items = this.dataSource.filteredData;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.max(items.indexOf(currentItem) - 1, 0);
      }
    }
    this.selection.clear();
    this.selection.select(items[idx]);
  }

  applySelection() {
    if (this.selection.hasValue() && this.selection.selected.length === 1) {
      const item = this.selection.selected[0];
      this.router.navigate(['/links', item.link.name], {
        queryParams: { c: this.yamcs.context }
      });
    }
  }

  ngOnDestroy() {
    this.selectionSubscription?.unsubscribe();
    this.linkSubscription?.cancel();
  }
}
