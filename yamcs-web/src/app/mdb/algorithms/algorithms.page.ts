import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Algorithm } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/yamcs.service';

@Component({
  templateUrl: './algorithms.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmsPageComponent {

  algorithms$: Observable<Algorithm[]>;

  constructor(yamcs: YamcsService) {
    this.algorithms$ = yamcs.getSelectedInstance().getAlgorithms();
  }
}
