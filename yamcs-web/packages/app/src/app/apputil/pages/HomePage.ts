import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, ViewChild } from '@angular/core';
import { MatPaginator, MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Instance } from '@yamcs/client';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './HomePage.html',
  styleUrls: ['./HomePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage implements AfterViewInit, OnDestroy {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  private instancesByName: { [key: string]: Instance } = {};

  dataSource = new MatTableDataSource<Instance>([]);
  selection = new SelectionModel<Instance>(true, []);

  instanceSubscription: Subscription;

  displayedColumns = [
    'select',
    'status',
    'name',
    'labels',
    // 'state',
    'actions',
  ];

  constructor(private yamcs: YamcsService, title: Title, private authService: AuthService) {
    title.setTitle('Yamcs');
    this.yamcs.yamcsClient.getInstances().then(instances => {
      for (const instance of instances) {
        this.instancesByName[instance.name] = instance;
      }
      this.dataSource.data = Object.values(this.instancesByName);
    });

    this.yamcs.yamcsClient.getInstanceUpdates().then(response => {
      this.instanceSubscription = response.instance$.subscribe(instance => {
        this.instancesByName[instance.name] = instance;
        this.dataSource.data = Object.values(this.instancesByName);
      });
    });

    this.dataSource.filterPredicate = (instance, filter) => {
      return instance.name.toLowerCase().indexOf(filter) >= 0;
    };
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
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
    this.yamcs.yamcsClient.editInstance(instance.name, {
      state: 'running',
    }).catch(err => {
      if (err.response) {
        err.response.json().then((json: any) => {
          alert(`Failed to start instance: ${json.msg}`);
        });
      } else {
        alert(err);
      }
    });
  }

  restartSelectedInstances() {
    for (const instance of this.selection.selected) {
      if (instance.state !== 'OFFLINE') {
        this.restartInstance(instance);
      }
    }
  }

  restartInstance(instance: Instance) {
    this.yamcs.yamcsClient.editInstance(instance.name, {
      state: 'restarted',
    }).catch(err => {
      if (err.response) {
        err.response.json().then((json: any) => {
          alert(`Failed to restart instance: ${json.msg}`);
        });
      } else {
        alert(err);
      }
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
    this.yamcs.yamcsClient.editInstance(instance.name, {
      state: 'stopped',
    }).catch(err => {
      if (err.response) {
        err.response.json().then((json: any) => {
          alert(`Failed to stop instance: ${json.msg}`);
        });
      } else {
        alert(err);
      }
    });
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

  ngOnDestroy() {
    if (this.instanceSubscription) {
      this.instanceSubscription.unsubscribe();
    }
  }
}
