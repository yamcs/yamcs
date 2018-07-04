import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { DisplayFolder, DisplaySource, Instance } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { YamcsService } from '../../core/services/YamcsService';
import { CreateDisplayDialog } from './CreateDisplayDialog';

@Component({
  templateUrl: './DisplayFolderPage.html',
  styleUrls: ['./DisplayFolderPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayFolderPage implements OnDestroy {

  instance: Instance;

  currentFolder$ = new BehaviorSubject<DisplayFolder | null>(null);
  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  displayedColumns = ['select', 'name', 'type', 'visibility'];
  dataSource = new MatTableDataSource<BrowseItem>([]);
  selection = new SelectionModel<BrowseItem>(true, []);

  routerSubscription: Subscription;

  constructor(
    private dialog: MatDialog,
    private yamcs: YamcsService,
    title: Title,
    router: Router,
    private route: ActivatedRoute,
  ) {
    title.setTitle('Displays - Yamcs');
    this.instance = yamcs.getInstance();

    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe(() => {
      let path = '';
      for (const segment of this.route.snapshot.url) {
        path += '/' + segment.path;
      }
      yamcs.getInstanceClient()!.getDisplayFolder(path).then(dir => {
        this.updateBrowsePath();
        this.changedir(dir);
      });
    });
  }

  private changedir(dir: DisplayFolder) {
    this.selection.clear();
    this.currentFolder$.next(dir);
    const items: BrowseItem[] = [];
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
        source: file.source,
      });
    }
    this.dataSource.data = items;
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows && numRows > 0;
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

  createDisplay() {
    let path = '';
    for (const segment of this.route.snapshot.url) {
      path += '/' + segment.path;
    }
    const dialogRef = this.dialog.open(CreateDisplayDialog, {
      width: '400px',
      data: {
        path: path || '/',
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      console.log(result);
    });
  }

  deleteSelectedDisplays() {
    if (confirm('Are you sure you want to delete the selected items?')) {
      const promises = [];
      for (const item of this.selection.selected) {
        promises.push(this.yamcs.getInstanceClient()!.deleteDisplay(item.path));
      }
      Promise.all(promises).then(() => {
        const currentFolder = this.currentFolder$.value!;
        this.yamcs.getInstanceClient()!.getDisplayFolder(currentFolder.path).then(dir => {
          this.updateBrowsePath();
          this.changedir(dir);
        });
      });
    }
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
  source?: DisplaySource;
}

interface BreadCrumbItem {
  name: string;
  route: string;
}
