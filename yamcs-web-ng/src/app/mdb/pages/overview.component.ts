import { Component, ChangeDetectionStrategy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs/Observable';

import { YamcsClient, Instance } from '../../../yamcs-client';

@Component({
  templateUrl: './overview.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OverviewPageComponent {

  instance$: Observable<Instance>;

  constructor(route: ActivatedRoute, http: HttpClient) {
    const client = new YamcsClient(http);
    this.instance$ = client.getInstance('simulator');
  }
}
