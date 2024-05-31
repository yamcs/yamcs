import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { HttpError, ListObjectsOptions, ListObjectsResponse, StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import * as dnd from '../../../shared/dnd';
import { StoragePageTemplateComponent } from '../../storage-page-template/storage-page-template.component';
import { StorageToolbarComponent } from '../../storage-toolbar/storage-toolbar.component';
import { BucketPageTabsComponent } from '../bucket-page-tabs/bucket-page-tabs.component';
import { CreateFolderDialogComponent } from '../create-folder-dialog/create-folder-dialog.component';
import { RenameObjectDialogComponent } from '../rename-object-dialog/rename-object-dialog.component';
import { Upload } from '../upload-progress-dialog/Upload';
import { UploadProgressDialogComponent } from '../upload-progress-dialog/upload-progress-dialog.component';
import { ViewObjectMetadataDialogComponent } from '../view-object-metadata-dialog/view-object-metadata-dialog.component';

@Component({
  standalone: true,
  templateUrl: './bucket-object-list.component.html',
  styleUrl: './bucket-object-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    BucketPageTabsComponent,
    WebappSdkModule,
    StoragePageTemplateComponent,
    StorageToolbarComponent,
  ],
})
export class BucketObjectListComponent implements OnDestroy {

  @ViewChild('droparea', { static: true })
  dropArea: ElementRef;

  @ViewChild('uploader')
  private uploaderEl: ElementRef<HTMLInputElement>;

  name: string;

  breadcrumb$ = new BehaviorSubject<BreadCrumbItem[]>([]);
  dragActive$ = new BehaviorSubject<boolean>(false);
  showPreview$ = new BehaviorSubject<boolean>(false);
  previewWidth$ = new BehaviorSubject<number>(600);

  displayedColumns = ['select', 'name', 'size', 'modified', 'actions'];
  dataSource = new MatTableDataSource<BrowseItem>([]);
  selection = new SelectionModel<BrowseItem>(true, []);

  uploads$ = new BehaviorSubject<Upload[]>([]);

  private routerSubscription: Subscription;
  private storageClient: StorageClient;

  private progressDialogOpen = false;

  private dialogRef: MatDialogRef<any>;

  constructor(
    private dialog: MatDialog,
    router: Router,
    private route: ActivatedRoute,
    yamcs: YamcsService,
    title: Title,
  ) {
    this.name = route.snapshot.parent!.parent!.paramMap.get('name')!;
    title.setTitle(this.name);
    this.storageClient = yamcs.createStorageClient();

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

    this.storageClient.listObjects(this.name, options).then(dir => {
      this.updateBrowsePath();
      this.changedir(dir);
    });
  }

  private changedir(dir: ListObjectsResponse) {
    this.selection.clear();
    const items: BrowseItem[] = [];
    for (const prefix of dir.prefixes || []) {
      items.push({
        folder: true,
        name: prefix,
      });
    }
    for (const object of dir.objects || []) {
      // Ignore fake objects that represent an empty directory
      if (object.name.endsWith('/')) {
        continue;
      }
      items.push({
        folder: false,
        name: object.name,
        modified: object.created,
        size: object.size,
        objectUrl: this.storageClient.getObjectURL(this.name, object.name),
        metadata: object.metadata,
      });
    }
    this.dataSource.data = items;
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

  toggleOne(row: BrowseItem) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  createFolder() {
    this.dialog.open(CreateFolderDialogComponent, {
      width: '400px',
      data: {
        bucket: this.name,
        path: this.getCurrentPath(),
      }
    }).afterClosed().subscribe({
      next: () => this.loadCurrentFolder(),
    });
  }

  uploadObjects() {
    let path = this.getCurrentPath();
    // Full path should not have a leading slash
    if (path.startsWith('/')) {
      path = path.substring(1);
    }

    const files = this.uploaderEl.nativeElement.files;

    const uploads: any[] = [];
    for (const key in files) {
      if (!isNaN(parseInt(key, 10))) {
        const file = files[key as any];
        const fullPath = path ? path + '/' + file.name : file.name;

        const bucket = this.name;
        const promise = this.storageClient.uploadObject(bucket, fullPath, file);
        uploads.push({ 'filename': file.name, promise });
      }
    }

    if (uploads.length) {
      this.showUploadProgress().then(() => {
        for (const upload of uploads) {
          this.trackUpload(upload);
        }
        this.settlePromises(uploads.map(u => u.promise)).then(() => {
          this.loadCurrentFolder();
        });
      });
    }
  }

  /**
   * Returns a promise that is always successful and that captures
   * the success/error of the passed promises.
   */
  private settlePromises(promises: Promise<any>[]) {
    return Promise.all(promises.map(promise => {
      return promise.then(
        value => ({ state: 'fullfilled', value }),
        value => ({ state: 'rejected', value })
      );
    }));
  }

  private trackUpload(upload: Upload) {
    upload.promise.then(() => {
      upload.complete = true;
      this.uploads$.next([... this.uploads$.value]);
    }).catch((err: HttpError) => {
      err.response.json().then(msg => {
        upload.complete = true;
        upload.err = msg['msg'];
        this.uploads$.next([... this.uploads$.value]);
      }).catch(() => {
        upload.complete = true;
        upload.err = err.statusText;
        this.uploads$.next([... this.uploads$.value]);
      });
    });
    this.uploads$.next([
      ...this.uploads$.value,
      upload,
    ]);
  }

  private getCurrentPath() {
    let path = '';
    for (const segment of this.route.snapshot.url) {
      path += '/' + segment.path;
    }
    return path || '/';
  }

  togglePreview() {
    this.showPreview$.next(!this.showPreview$.value);
  }

  deleteSelectedObjects() {
    const deletableObjects: string[] = [];
    const findObjectPromises = [];
    for (const item of this.selection.selected) {
      if (item.folder) {
        findObjectPromises.push(this.storageClient.listObjects(this.name, {
          prefix: item.name,
        }).then(response => {
          const objects = response.objects || [];
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
          deletePromises.push(this.storageClient.deleteObject(this.name, object));
        }

        Promise.all(deletePromises).then(() => {
          this.loadCurrentFolder();
        });
      }
    });
  }

  renameFile(item: BrowseItem) {
    const dialogRef = this.dialog.open(RenameObjectDialogComponent, {
      data: {
        bucket: this.name,
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

  deleteFile(item: BrowseItem) {
    if (confirm(`Are you sure you want to delete ${item.name}?`)) {
      this.storageClient.deleteObject(this.name, item.name).then(() => {
        this.loadCurrentFolder();
      });
    }
  }

  openViewMetadataDialog(item: BrowseItem) {
    this.dialog.open(ViewObjectMetadataDialogComponent, {
      width: '600px',
      data: { metadata: item.metadata || {} },
    });
  }

  dragEnter(evt: DragEvent) {
    this.dragActive$.next(true);
    evt.preventDefault();
    evt.stopPropagation();
    return false;
  }

  dragOver(evt: DragEvent) { // This event must be prevented. Otherwise drop doesn't trigger.
    evt.preventDefault();
    evt.stopPropagation();
    return false;
  }

  dragLeave(evt: DragEvent) {
    this.dragActive$.next(false);
    evt.preventDefault();
    evt.stopPropagation();
    return false;
  }

  drop(evt: DragEvent) {
    const dataTransfer: any = evt.dataTransfer || {};
    if (dataTransfer) {
      let objectPrefix = this.getCurrentPath().substring(1);
      if (objectPrefix !== '') {
        objectPrefix += '/';
      }

      dnd.listDroppedFiles(dataTransfer).then(droppedFiles => {
        if (droppedFiles.length) {
          const uploadPromises: any[] = [];

          this.showUploadProgress().then(() => {
            for (const droppedFile of droppedFiles) {
              const objectPath = objectPrefix + droppedFile._fullPath;
              const promise = this.storageClient.uploadObject(this.name, objectPath, droppedFile);
              this.trackUpload({ filename: droppedFile._fullPath, promise });
              uploadPromises.push(promise);
            }

            this.settlePromises(uploadPromises).then(() => {
              this.loadCurrentFolder();
            });
          });
        }
      });
    }
    this.dragActive$.next(false);
    evt.preventDefault();
    evt.stopPropagation();
    return false;
  }

  private updateBrowsePath() {
    const breadcrumb: BreadCrumbItem[] = [];
    let path = '';
    for (const segment of this.route.snapshot.url) {
      path += '/' + segment.path;
      breadcrumb.push({
        name: segment.path,
        route: `/storage/buckets/${this.name}/objects` + path,
      });
    }
    this.breadcrumb$.next(breadcrumb);
    return path || '/';
  }

  isImage(item: BrowseItem) {
    if (item.folder) {
      return false;
    }
    const lc = item.name.toLocaleLowerCase();
    return lc.endsWith('.png') || lc.endsWith('.gif') || lc.endsWith('.jpg')
      || lc.endsWith('jpeg') || lc.endsWith('bmp') || lc.endsWith('svg') || lc.endsWith('ico');
  }

  private async showUploadProgress() {
    if (this.progressDialogOpen) {
      return;
    }

    this.dialogRef = this.dialog.open(UploadProgressDialogComponent, {
      position: {
        bottom: '10px',
        right: '10px',
      },
      width: '500px',
      data: {
        uploads$: this.uploads$
      },
      hasBackdrop: false,
      autoFocus: false,
      panelClass: ['progress-dialog', 'elevation-z1'],
    });
    this.dialogRef.afterOpened().subscribe(() => this.progressDialogOpen = true);
    this.dialogRef.afterClosed().subscribe(() => {
      this.uploads$.next([]);
      this.progressDialogOpen = false;
    });

    return new Promise<any>((resolve, reject) => {
      this.dialogRef.afterOpened().subscribe(() => {
        resolve(true);
      }, err => {
        reject(err);
      });
    });
  }

  resizeMouseDown(event: MouseEvent) {
    let resizeGrabX: number | null = event.clientX;
    const originalWidth = this.previewWidth$.value;

    const mousemoveListener = (moveEvent: MouseEvent) => {
      if (resizeGrabX !== null) {
        const newWidth = originalWidth - (moveEvent.clientX - resizeGrabX);
        this.previewWidth$.next(Math.max(400, newWidth));
      }
    };
    const mouseupListener = (upEvent: MouseEvent) => {
      resizeGrabX = null;
      document.removeEventListener('mousemove', mousemoveListener);
      document.removeEventListener('mouseup', mouseupListener);
    };

    document.addEventListener('mousemove', mousemoveListener);
    document.addEventListener('mouseup', mouseupListener);
  }

  ngOnDestroy() {
    this.routerSubscription?.unsubscribe();
    this.dialogRef?.close();
  }
}

export class BrowseItem {
  folder: boolean;
  name: string;
  modified?: string;
  objectUrl?: string;
  size?: number;
  metadata?: { [key: string]: string; };
}

export interface BreadCrumbItem {
  name: string;
  route: string;
}
