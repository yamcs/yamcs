import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Instance, Algorithm } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './SpaceSystemAlgorithmsTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemAlgorithmsTab implements OnInit {

  instance$: Observable<Instance>;
  algorithms$: Observable<Algorithm[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>) {
    const parent = route.snapshot.parent;
    if (parent) {
      const qualifiedName = parent.paramMap.get('qualifiedName');
      if (qualifiedName != null) {
        this.algorithms$ = yamcs.getSelectedInstance().getAlgorithms({
          namespace: qualifiedName
        });
      }
    }
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
