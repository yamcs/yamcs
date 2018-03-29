import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance } from '@yamcs/client';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SpaceSystemAlgorithmTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemAlgorithmTab {

  instance$: Observable<Instance>;
  algorithm$: Promise<Algorithm>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.algorithm$ = yamcs.getSelectedInstance().getAlgorithm(qualifiedName);
  }
}
