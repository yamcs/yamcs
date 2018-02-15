import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Container } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ContainersPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContainersPage {

  containers$: Observable<Container[]>;

  constructor(yamcs: YamcsService) {
    this.containers$ = yamcs.getSelectedInstance().getContainers();
  }
}
