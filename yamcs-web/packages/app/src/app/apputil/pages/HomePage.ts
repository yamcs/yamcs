import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatPaginator, MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/services/AuthService';
import { YamcsService } from '../../core/services/YamcsService';


@Component({
  templateUrl: './HomePage.html',
  styleUrls: ['./HomePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  @ViewChild(MatPaginator)
  paginator: MatPaginator;

  instances$ = new BehaviorSubject<Instance[]>([]);

  dataSource = new MatTableDataSource<Instance>([]);

  displayedColumns = [
    'name',
    'state',
    'actions',
  ];

  constructor(private yamcs: YamcsService, title: Title, private authService: AuthService) {
    title.setTitle('Yamcs');
    this.refreshInstances();

    this.dataSource.filterPredicate = (instance, filter) => {
      return instance.name.toLowerCase().indexOf(filter) >= 0;
    };
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  private refreshInstances() {
    this.yamcs.yamcsClient.getInstances().then(instances => {
      this.dataSource.data = instances;
    });
  }

  applyFilter(value: string) {
    this.dataSource.filter = value.trim().toLowerCase();
  }

  restartInstance(instance: Instance) {
    this.yamcs.yamcsClient.editInstance(instance.name, {
      state: 'restarted',
    }).then(() => {
      this.refreshInstances();
    }).catch(err => {
      this.refreshInstances();
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
    }).then(() => {
      this.refreshInstances();
    }).catch(err => {
      this.refreshInstances();
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
}
