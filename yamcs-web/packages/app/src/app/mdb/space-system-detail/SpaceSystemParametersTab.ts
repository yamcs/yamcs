import { Component, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { Observable } from 'rxjs/Observable';

import { Instance, Parameter } from '@yamcs/client';

import { ActivatedRoute } from '@angular/router';

import { YamcsService } from '../../core/services/YamcsService';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';

@Component({
  templateUrl: './SpaceSystemParametersTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemParametersTab implements OnInit {

  qualifiedName: string;

  instance$: Observable<Instance>;
  parameters$: Promise<Parameter[]>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, private store: Store<State>) {
    const parent = route.snapshot.parent!;
    this.qualifiedName = parent.paramMap.get('qualifiedName')!;
    this.parameters$ = yamcs.getSelectedInstance().getParameters({
      namespace: this.qualifiedName
    });
  }

  ngOnInit() {
    this.instance$ = this.store.select(selectCurrentInstance);
  }
}
