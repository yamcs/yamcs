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

  instanceSubscription: Subscription;

  displayedColumns = [
    'status',
    'name',
    'state',
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

  mayControlServices() {
    return this.authService.getUser()!.hasSystemPrivilege('ControlServices');
  }

  ngOnDestroy() {
    if (this.instanceSubscription) {
      this.instanceSubscription.unsubscribe();
    }
  }
}
