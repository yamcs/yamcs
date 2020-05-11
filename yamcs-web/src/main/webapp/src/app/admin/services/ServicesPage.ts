import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Service } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';

@Component({
  templateUrl: './ServicesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesPage {

  instance = '_global';

  filterForm = new FormGroup({
    instance: new FormControl('_global'),
  });

  instanceOptions$ = new BehaviorSubject<Option[]>([
    { id: '_global', label: '_global' },
  ]);

  dataSource = new MatTableDataSource<Service>();

  constructor(
    private yamcs: YamcsService,
    private router: Router,
    private route: ActivatedRoute,
    title: Title,
  ) {
    title.setTitle('Services');

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
    this.refresh();

    this.filterForm.get('instance')!.valueChanges.forEach(instance => {
      this.instance = instance;
      this.refresh();
    });
  }

  private initializeOptions() {
    const queryParams = this.route.snapshot.queryParamMap;
    if (queryParams.has('instance')) {
      this.instance = queryParams.get('instance')!;
      this.filterForm.get('instance')!.setValue(this.instance);
    }
  }

  startService(name: string) {
    if (confirm(`Are you sure you want to start ${name} ?`)) {
      const instance = this.filterForm.get('instance')!.value;
      this.yamcs.yamcsClient.startService(instance, name).then(() => this.refresh());
    }
  }

  stopService(name: string) {
    if (confirm(`Are you sure you want to stop ${name} ?`)) {
      const instance = this.filterForm.get('instance')!.value;
      this.yamcs.yamcsClient.stopService(instance, name).then(() => this.refresh());
    }
  }

  refresh() {
    this.updateURL();
    const instance = this.filterForm.get('instance')!.value;
    this.yamcs.yamcsClient.getServices(instance).then(services => {
      this.dataSource.data = services;
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
}
