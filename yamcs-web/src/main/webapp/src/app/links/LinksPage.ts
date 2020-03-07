import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Instance, LinkEvent, LinkSubscription } from '../client';
import { AuthService } from '../core/services/AuthService';
import { YamcsService } from '../core/services/YamcsService';
import { ColumnInfo } from '../shared/template/ColumnChooser';
import { LinkItem } from './LinkItem';

@Component({
  templateUrl: './LinksPage.html',
  styleUrls: ['./LinksPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LinksPage implements AfterViewInit, OnDestroy {

  instance: Instance;

  filterControl = new FormControl();

  // Link to show detail pane (only on single selection)
  detailLink$ = new BehaviorSubject<LinkItem | null>(null);

  columns: ColumnInfo[] = [
    { id: 'select', label: '', alwaysVisible: true },
    { id: 'status', label: '', alwaysVisible: true },
    { id: 'name', label: 'Name', alwaysVisible: true },
    { id: 'className', label: 'Class Name' },
    { id: 'in', label: 'In Count', visible: true },
    { id: 'out', label: 'Out Count', visible: true },
    { id: 'actions', label: '', alwaysVisible: true },
  ];

  dataSource = new MatTableDataSource<LinkItem>();
  selection = new SelectionModel<LinkItem>(true, []);

  private selectionSubscription: Subscription;
  private linkSubscription: LinkSubscription;

  private itemsByName: { [key: string]: LinkItem; } = {};

  constructor(
    private yamcs: YamcsService,
    private authService: AuthService,
    title: Title,
    private changeDetection: ChangeDetectorRef,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Links');
    this.instance = yamcs.getInstance();

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
    });

    // Fetch with REST first, otherwise may take up to a second
    // before we get an update via websocket.
    this.yamcs.yamcsClient.getLinks(this.instance.name).then(links => {
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
        instance: this.instance.name,
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

  enableLink(name: string) {
    this.yamcs.yamcsClient.enableLink(this.instance.name, name);
  }

  disableLink(name: string) {
    this.yamcs.yamcsClient.disableLink(this.instance.name, name);
  }

  resetCounters(name: string) {
    this.yamcs.yamcsClient.editLink(this.instance.name, name, {
      resetCounters: true,
    });
  }

  mayControlLinks() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlLinks');
  }

  private processLinkEvent(evt: LinkEvent) {
    // Update detail pane
    const selectedItem = this.detailLink$.value;
    if (selectedItem && selectedItem.link.name === evt.linkInfo.name) {
      selectedItem.link = evt.linkInfo;
      for (const subitem of Object.values(this.itemsByName)) {
        if (subitem.link.parentName === evt.linkInfo.name) {
          subitem.parentLink = evt.linkInfo;
        }
      }
      this.detailLink$.next({ ...selectedItem });
    }

    switch (evt.type) {
      case 'REGISTERED':
      case 'UPDATED':
        this.itemsByName[evt.linkInfo.name].link = evt.linkInfo;
        for (const item of Object.values(this.itemsByName)) {
          if (item.parentLink && item.parentLink.name === evt.linkInfo.name) {
            item.parentLink = evt.linkInfo;
          }
        }
        this.updateDataSource();
        break;
      case 'UNREGISTERED':
        const item = this.itemsByName[evt.linkInfo.name];
        this.selection.deselect(item);
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
    const numRows = this.dataSource.data.length;
    return numSelected === numRows;
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.data.forEach(row => this.selection.select(row));
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

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  ngOnDestroy() {
    if (this.selectionSubscription) {
      this.selectionSubscription.unsubscribe();
    }
    if (this.linkSubscription) {
      this.linkSubscription.cancel();
    }
  }
}
