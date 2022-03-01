import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Bucket, StorageClient } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';
import { CreateBucketDialog } from './CreateBucketDialog';

@Component({
  templateUrl: './BucketsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BucketsPage implements AfterViewInit {

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  instance = '_global';

  displayedColumns = [
    'select',
    'name',
    'size',
    'numObjects',
    'actions',
  ];

  filterForm = new FormGroup({
    instance: new FormControl('_global'),
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
    });

    this.initializeOptions();
    this.refreshDataSources();

    this.filterForm.get('instance')!.valueChanges.forEach(instance => {
      this.instance = instance;
      this.refreshDataSources();
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('instance')) {
      this.instance = queryParams.get('instance')!;
      this.filterForm.get('instance')!.setValue(this.instance);
    }
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
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
        this.refreshDataSources();
      }
    });
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
        this.refreshDataSources();
      });
    }
  }

  private refreshDataSources() {
    this.updateURL();
    this.storageClient.getBuckets(this.instance).then(buckets => {
      this.dataSource.data = buckets;
    });
  }

  private updateURL() {
    this.router.navigate([], {
      replaceUrl: true,
      relativeTo: this.route,
      queryParams: {
        instance: this.instance || null,
      },
      queryParamsHandling: 'merge',
    });
  }
}
