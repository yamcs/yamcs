import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Service } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './services.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesPageComponent {

  services$: Observable<Service[]>;

  constructor(yamcs: YamcsService) {
    this.services$ = yamcs.getSelectedInstance().getServices();
  }
}
