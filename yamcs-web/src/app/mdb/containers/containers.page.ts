import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Container } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './containers.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainersPageComponent {

  containers$: Observable<Container[]>;

  constructor(yamcs: YamcsService) {
    this.containers$ = yamcs.getSelectedInstance().getContainers();
  }
}
