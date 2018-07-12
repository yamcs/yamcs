import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Instance, ListObjectsOptions, ListObjectsResponse } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';
import { CreateDisplayDialog } from './CreateDisplayDialog';
import { RenameDisplayDialog } from './RenameDisplayDialog';

@Component({
  templateUrl: './DisplayFolderPage.html',
  styleUrls: ['./DisplayFolderPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayFolderPage implements OnDestroy {

  instance: Instance;

  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);

  displayedColumns = ['select', 'name', 'type', 'modified', 'actions'];
  dataSource = new MatTableDataSource<BrowseItem>([]);
  selection = new SelectionModel<BrowseItem>(true, []);

  private routerSubscription: Subscription;

  constructor(
    private dialog: MatDialog,
    private yamcs: YamcsService,
    title: Title,
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
  ) {
    title.setTitle('Displays - Yamcs');
    this.instance = yamcs.getInstance();

    this.loadCurrentFolder();
    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe(() => {
      this.loadCurrentFolder();
    });
  }

  private loadCurrentFolder() {
    const options: ListObjectsOptions = {
      delimiter: '/',
    };
    const routeSegments = this.route.snapshot.url;
    if (routeSegments.length) {
      options.prefix = routeSegments.map(s => s.path).join('/') + '/';
    }
    this.yamcs.getInstanceClient()!.listObjects('displays', options).then(dir => {
      this.updateBrowsePath();
      this.changedir(dir);
    });
  }

  private changedir(dir: ListObjectsResponse) {
    this.selection.clear();
    const items: BrowseItem[] = [];
    for (const prefix of dir.prefix || []) {
      items.push({
        folder: true,
        name: prefix,
      });
    }
    for (const object of dir.object || []) {
      items.push({
        folder: false,
        name: object.name,
        modified: object.created,
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
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
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
      if (result) {
        this.router.navigateByUrl(`/monitor/displays/files/${result}?instance=${this.instance.name}`);
      }
    });
  }

  deleteSelectedDisplays() {
    const deletableObjects: string[] = [];
    const findObjectPromises = [];
    for (const item of this.selection.selected) {
      if (item.folder) {
        findObjectPromises.push(this.yamcs.getInstanceClient()!.listObjects('displays', {
          prefix: item.name,
        }).then(response => {
          const objects = response.object || [];
          deletableObjects.push(...objects.map(o => o.name));
        }));
      } else {
        deletableObjects.push(item.name);
      }
    }

    Promise.all(findObjectPromises).then(() => {
      if (confirm(`You are about to delete ${deletableObjects.length} files. Are you sure you want to continue?`)) {
        const deletePromises = [];
        for (const object of deletableObjects) {
          deletePromises.push(this.yamcs.getInstanceClient()!.deleteObject('displays', object));
        }

        Promise.all(deletePromises).then(() => {
          this.loadCurrentFolder();
        });
      }
    });
  }

  renameFile(item: BrowseItem) {
    const dialogRef = this.dialog.open(RenameDisplayDialog, {
      data: {
        name: item.name,
      },
      width: '400px',
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadCurrentFolder();
      }
    });
  }

  mayManageDisplays() {
    const user = this.authService.getUser()!;
    return user.hasObjectPrivilege('ManageBucket', 'displays')
      || user.hasSystemPrivilege('ManageAnyBucket');
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

export class BrowseItem {
  folder: boolean;
  name: string;
  modified?: string;
}

export interface BreadCrumbItem {
  name: string;
  route: string;
}
