import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Algorithm } from '../../../yamcs-client';

import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './AlgorithmsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmsPage {

  algorithms$: Observable<Algorithm[]>;

  constructor(yamcs: YamcsService) {
    this.algorithms$ = yamcs.getSelectedInstance().getAlgorithms();
  }
}
