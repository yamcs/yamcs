import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance } from '../../../yamcs-client';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './AlgorithmPage.html',
  styleUrls: ['./AlgorithmPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlgorithmPage {

  qualifiedName: string;

  instance$: Observable<Instance>;
  algorithm$: Observable<Algorithm>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);
    this.qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.algorithm$ = yamcs.getSelectedInstance().getAlgorithm(this.qualifiedName);
    this.algorithm$.subscribe(aa => console.log(aa));
  }
}
