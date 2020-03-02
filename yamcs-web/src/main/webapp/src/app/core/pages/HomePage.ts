import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance, InstancesSubscription } from '../../client';
import { AuthService } from '../services/AuthService';
import { MessageService } from '../services/MessageService';
import { YamcsService } from '../services/YamcsService';


@Component({
  templateUrl: './HomePage.html',
  styleUrls: ['./HomePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage implements AfterViewInit, OnDestroy {

  filterControl = new FormControl();

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  @ViewChild(MatPaginator, { static: true })
  paginator: MatPaginator;

  private instancesByName: { [key: string]: Instance; } = {};

  dataSource = new MatTableDataSource<Instance>([]);
  selection = new SelectionModel<Instance>(true, []);

  instancesSubscription: InstancesSubscription;

  displayedColumns = [
    'select',
    'status',
    'name',
    'labels',
    // 'state',
    'actions',
  ];

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private authService: AuthService,
    private messageService: MessageService,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    title.setTitle('Instances');

    this.dataSource.filterPredicate = (instance, filter) => {
      return instance.name.toLowerCase().indexOf(filter) >= 0;
    };
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

    this.yamcs.yamcsClient.getInstances().then(instances => {
      for (const instance of instances) {
        this.instancesByName[instance.name] = instance;
      }
      this.dataSource.data = Object.values(this.instancesByName);

      this.instancesSubscription = this.yamcs.yamcsClient.createInstancesSubscription(instance => {
        this.instancesByName[instance.name] = instance;
        this.dataSource.data = Object.values(this.instancesByName);
      });
    });

    this.dataSource.paginator = this.paginator;
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

  toggleOne(row: Instance) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  startSelectedInstances() {
    for (const instance of this.selection.selected) {
      if (instance.state === 'OFFLINE') {
        this.startInstance(instance);
      }
    }
  }

  startInstance(instance: Instance) {
    this.yamcs.yamcsClient.startInstance(instance.name)
      .catch(err => this.messageService.showError(err));
  }

  restartSelectedInstances() {
    for (const instance of this.selection.selected) {
      if (instance.state !== 'OFFLINE') {
        this.restartInstance(instance);
      }
    }
  }

  restartInstance(instance: Instance) {
    this.yamcs.yamcsClient.restartInstance(instance.name)
      .catch(err => {
        console.log('got error', err);
        this.messageService.showError(err);
      });
  }

  stopSelectedInstances() {
    for (const instance of this.selection.selected) {
      if (instance.state !== 'OFFLINE') {
        this.stopInstance(instance);
      }
    }
  }

  stopInstance(instance: Instance) {
    this.yamcs.yamcsClient.stopInstance(instance.name)
      .catch(err => this.messageService.showError(err));
  }

  isGroupStartEnabled() {
    // Allow if at least one of the selected items is startable
    for (const instance of this.selection.selected) {
      if (instance.state === 'OFFLINE') {
        return true;
      }
    }
    return false;
  }

  isGroupStopEnabled() {
    // Allow if at least one of the selected items is stoppable
    for (const instance of this.selection.selected) {
      if (instance.state !== 'OFFLINE') {
        return true;
      }
    }
    return false;
  }

  isGroupRestartEnabled() {
    // Allow if at least one of the selected items is restartable
    for (const instance of this.selection.selected) {
      if (instance.state !== 'OFFLINE') {
        return true;
      }
    }
    return false;
  }

  mayControlServices() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlServices');
  }

  mayCreateInstances() {
    const user = this.authService.getUser()!;
    return user.hasSystemPrivilege('CreateInstances');
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
    if (this.instancesSubscription) {
      this.instancesSubscription.cancel();
    }
  }
}
