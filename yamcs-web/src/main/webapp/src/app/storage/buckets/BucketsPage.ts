import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { UntypedFormControl, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Bucket, StorageClient } from '../../client';
import { AuthService } from '../../core/services/AuthService';
import { MessageService } from '../../core/services/MessageService';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';
import { CreateBucketDialog } from './CreateBucketDialog';

@Component({
  templateUrl: './BucketsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BucketsPage implements AfterViewInit {

  filterControl = new UntypedFormControl();

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  instance = '_global';

  displayedColumns = [
    'select',
    'name',
    'created',
    'size',
    'avail',
    'capacity',
    'numObjects',
    'availObjects',
    'pctObjects',
    'actions',
  ];

  filterForm = new UntypedFormGroup({
    instance: new UntypedFormControl('_global'),
  });

  instanceOptions$ = new BehaviorSubject<Option[]>([
    { id: '_global', label: '_global' },
  ]);

  dataSource = new MatTableDataSource<Bucket>();
  selection = new SelectionModel<Bucket>(true, []);

  private storageClient: StorageClient;

  constructor(
    private yamcs: YamcsService,
    private dialog: MatDialog,
    private router: Router,
    private route: ActivatedRoute,
    private messageService: MessageService,
    private authService: AuthService,
    title: Title,
  ) {
    title.setTitle('Buckets');
    this.storageClient = this.yamcs.createStorageClient();

    yamcs.yamcsClient.getInstances({
      filter: 'state=RUNNING',
    }).then(instances => {
      for (const instance of instances) {
        this.instanceOptions$.next([
          ...this.instanceOptions$.value,
          {
            id: instance.name,
            label: instance.name,
          }
        ]);
      }
    }).catch(err => this.messageService.showError(err));

    this.initializeOptions();
    this.refreshView();

    this.filterForm.get('instance')!.valueChanges.forEach(instance => {
      this.instance = instance;
      this.refreshView();
    });
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('filter')) {
      this.filterControl.setValue(queryParams.get('filter'));
      this.dataSource.filter = queryParams.get('filter')!.toLowerCase();
    }
    if (queryParams.has('instance')) {
      this.instance = queryParams.get('instance')!;
      this.filterForm.get('instance')!.setValue(this.instance);
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

  toggleOne(row: Bucket) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  createBucket() {
    const dialogRef = this.dialog.open(CreateBucketDialog, {
      width: '400px',
      data: {
        bucketInstance: this.instance,
      },
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.refreshView();
      }
    });
  }

  zeroOrMore(value: number) {
    return Math.max(0, value);
  }

  mayManageBuckets() {
    return this.authService.getUser()!.hasSystemPrivilege('ManageAnyBucket');
  }

  deleteSelectedBuckets() {
    if (confirm('Are you sure you want to delete the selected buckets?')) {
      const deletePromises = [];
      for (const bucket of this.selection.selected) {
        const promise = this.storageClient.deleteBucket(this.instance, bucket.name);
        deletePromises.push(promise);
      }

      Promise.all(deletePromises).then(() => {
        this.selection.clear();
        this.refreshView();
      });
    }
  }

  deleteBucket(bucket: Bucket) {
    if (confirm(`Are you sure you want to delete the bucket ${bucket.name}?`)) {
      this.storageClient.deleteBucket(this.instance, bucket.name).then(() => {
        this.selection.clear();
        this.refreshView();
      }).catch(err => this.messageService.showError(err));
    }
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
      this.router.navigate(['/buckets', this.instance, item.name]);
    }
  }

  private refreshView() {
    this.updateURL();
    this.storageClient.getBuckets(this.instance).then(buckets => {
      this.dataSource.data = buckets;
    }).catch(err => this.messageService.showError(err));
  }

  private updateURL() {
    const filterValue = this.filterControl.value;
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        instance: this.instance || null,
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }
}
