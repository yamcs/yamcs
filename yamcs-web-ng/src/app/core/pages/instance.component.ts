import { Component, ChangeDetectionStrategy } from '@angular/core';
import { YamcsClient, Instance } from '../../../yamcs-client';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { ActivatedRoute } from '@angular/router';

@Component({
  template: '<router-outlet></router-outlet>',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstancePageComponent {

  instance$: Observable<Instance>;

  constructor(route: ActivatedRoute, http: HttpClient) {
    const parentRoute = route.parent;
    if (parentRoute !== null) {
      const instanceName = parentRoute.snapshot.paramMap.get('instance');
      if (instanceName !== null) {
        const client = new YamcsClient(http);
        this.instance$ = client.getInstance(instanceName);
      }
    }
  }
}
