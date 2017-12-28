import { Component, ChangeDetectionStrategy } from '@angular/core';
import { YamcsClient, Instance } from '../../../yamcs-client';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

@Component({
  templateUrl: './instances.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstancesPageComponent {

  instances$: Observable<Instance[]>;

  constructor(http: HttpClient) {
    const client = new YamcsClient(http);
    this.instances$ = client.getInstances();
  }
}
