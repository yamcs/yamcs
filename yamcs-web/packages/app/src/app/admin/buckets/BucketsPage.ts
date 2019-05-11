import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatDialog, MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Bucket, StorageClient } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/template/Select';
import { CreateBucketDialog } from './CreateBucketDialog';

@Component({
  templateUrl: './BucketsPage.html',
  styleUrls: ['./BucketsPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BucketsPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  instance = '_global';

  displayedColumns = [
    'select',
    'name',
    'size',
    'numObjects',
  ];

  filterForm = new FormGroup({
    instance: new FormControl('_global'),
  });

  instanceOptions$ = new BehaviorSubject<Option[]>([
    { id: '_global', label: '_global', selected: true },
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
            selected: this.filterForm.get('instance')!.value === instance.name,
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
      for (const option of this.instanceOptions$.value) {
        option.selected = (option.id === this.instance);
      }
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
      relativeTo: this.route,
      queryParams: {
        instance: this.instance || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  updateInstance(instance: string) {
    this.filterForm.get('instance')!.setValue(instance);
  }
}
