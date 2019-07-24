import { SelectionModel } from '@angular/cdk/collections';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance } from '@yamcs/client';
import { fromEvent, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { AuthService } from '../services/AuthService';
import { YamcsService } from '../services/YamcsService';


@Component({
  templateUrl: './HomePage.html',
  styleUrls: ['./HomePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage implements AfterViewInit, OnDestroy {

  @ViewChild('filter', { static: true })
  filter: ElementRef;

  @ViewChild(MatSort, { static: true })
  sort: MatSort;

  @ViewChild(MatPaginator, { static: true })
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

  constructor(
    private yamcs: YamcsService,
    title: Title,
    private authService: AuthService,
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
      this.filter.nativeElement.value = queryParams.get('filter');
      this.dataSource.filter = queryParams.get('filter')!.toLowerCase();
    }

    this.yamcs.yamcsClient.getInstances().then(instances => {
      for (const instance of instances) {
        this.instancesByName[instance.name] = instance;
      }
      this.dataSource.data = Object.values(this.instancesByName);

      this.yamcs.yamcsClient.getInstanceUpdates().then(response => {
        this.instanceSubscription = response.instance$.subscribe(instance => {
          this.instancesByName[instance.name] = instance;
          this.dataSource.data = Object.values(this.instancesByName);
        });
      });
    });

    fromEvent(this.filter.nativeElement, 'keyup').pipe(
      debounceTime(150), // Keep low -- Client-side filter
      map(() => this.filter.nativeElement.value.trim()), // Detect 'distinct' on value not on KeyEvent
      distinctUntilChanged(),
    ).subscribe(value => {
      this.updateURL();
      this.dataSource.filter = value.toLowerCase();
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

  private updateURL() {
    const filterValue = this.filter.nativeElement.value.trim();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        filter: filterValue || null,
      },
      queryParamsHandling: 'merge',
    });
  }

  ngOnDestroy() {
    if (this.instanceSubscription) {
      this.instanceSubscription.unsubscribe();
    }
  }
}
