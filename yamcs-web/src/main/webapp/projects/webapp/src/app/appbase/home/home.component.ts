import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfigService, Instance, InstancesSubscription, MessageService, YamcsService, utils } from '@yamcs/webapp-sdk';
import { AuthService } from '../../core/services/AuthService';

import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class HomeComponent implements AfterViewInit, OnDestroy {

  filterControl = new UntypedFormControl();

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
    'processor',
    'labels',
    'template',
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
    private config: ConfigService,
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

      for (const item of this.selection.selected) {
        if (this.dataSource.filteredData.indexOf(item) === -1) {
          this.selection.deselect(item);
        }
      }
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
    const numRows = this.dataSource.filteredData.length;
    return numSelected === numRows && numRows > 0;
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.filteredData.forEach(row => this.selection.select(row));
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

  isCreateInstanceEnabled() {
    const user = this.authService.getUser()!;
    return this.config.hasTemplates() && user.hasSystemPrivilege('CreateInstances');
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
      const items = this.dataSource.data;
      if (items.indexOf(item) !== -1 && item.state !== 'OFFLINE') {
        if (item.processors?.length) {
          this.router.navigate(['/instance'], {
            queryParams: { c: item.name + '__' + utils.getDefaultProcessor(item) }
          });
        } else {
          this.router.navigate(['/instance'], {
            queryParams: { c: item.name }
          });
        }
      }
    }
  }

  ngOnDestroy() {
    this.instancesSubscription?.cancel();
  }
}
