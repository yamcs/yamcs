import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { DisplayFolder, Instance } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './DisplayFolderPage.html',
  styleUrls: ['./DisplayFolderPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayFolderPage implements OnDestroy {

  instance: Instance;

  displayInfo: DisplayFolder;
  currentFolder$ = new BehaviorSubject<DisplayFolder | null>(null);
  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  displayedColumns = ['select', 'name', 'type', 'visibility'];
  dataSource = new MatTableDataSource<BrowseItem>([]);
  selection = new SelectionModel<BrowseItem>(true, []);

  routerSubscription: Subscription;

  constructor(
    private dialog: MatDialog,
    yamcs: YamcsService,
    title: Title,
    router: Router,
    private route: ActivatedRoute,
  ) {
    title.setTitle('Displays - Yamcs');
    this.instance = yamcs.getInstance();

    yamcs.getInstanceClient()!.getDisplayInfo().then(displayInfo => {
      this.displayInfo = displayInfo;
      this.openFolderForPath();
      this.routerSubscription = router.events.pipe(
        filter(evt => evt instanceof NavigationEnd)
      ).subscribe(() => this.openFolderForPath());
    });
  }

  private openFolderForPath() {
    const path = this.updateBrowsePath();
    const folder = this.findDisplayFolder(path, this.displayInfo);
    if (folder) {
      this.changedir(folder);
    }
  }

  private changedir(dir: DisplayFolder) {
    this.currentFolder$.next(dir);
    const items = [];
    for (const folder of dir.folder || []) {
      items.push({
        folder: true,
        name: folder.name,
        path: folder.path,
        route: '/monitor/displays/browse' + folder.path,
      });
    }
    for (const file of dir.file || []) {
      items.push({
        folder: false,
        name: file.name,
        path: file.path,
        route: '/monitor/displays/files' + file.path,
      });
    }
    this.dataSource.data = items;
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

  toggleOne(row: BrowseItem) {
    this.selection.clear();
    this.selection.toggle(row);
  }

  private updateBrowsePath() {
    const breadcrumb: BreadCrumbItem[] = [];
    let path = '';
    for (const segment of this.route.snapshot.url) {
      path += '/' + segment.path;
      breadcrumb.push({
        name: segment.path,
        route: '/monitor/displays/browse' + path,
      });
    }
    this.breadcrumb$.next(breadcrumb);
    return path || '/';
  }

  private findDisplayFolder(path: string, start: DisplayFolder): DisplayFolder | undefined {
    if (path === '/') {
      return this.displayInfo;
    }

    for (const folder of start.folder || []) {
      if (folder.path === path) {
        return folder;
      } else {
        const sub = this.findDisplayFolder(path, folder);
        if (sub) {
          return sub;
        }
      }
    }
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }
}

class BrowseItem {
  folder: boolean;
  name: string;
  path: string;
  route: string;
}

interface BreadCrumbItem {
  name: string;
  route: string;
}
