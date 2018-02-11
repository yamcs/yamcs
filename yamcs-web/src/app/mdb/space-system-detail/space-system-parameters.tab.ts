import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Instance, Parameter } from '../../../yamcs-client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/yamcs.service';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './space-system-parameters.tab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemParametersTabComponent implements OnInit {

  instance$: Observable<Instance>;
  parameters$: Observable<Parameter[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>) {
    const parent = route.snapshot.parent;
    if (parent) {
      const qualifiedName = parent.paramMap.get('qualifiedName');
      if (qualifiedName != null) {
        this.parameters$ = yamcs.getSelectedInstance().getParameters({
          namespace: qualifiedName
        });
      }
    }
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
