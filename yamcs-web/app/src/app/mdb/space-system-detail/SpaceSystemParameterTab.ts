import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance, Parameter } from '../../../yamcs-client';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './SpaceSystemParameterTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpaceSystemParameterTab {

  instance$: Observable<Instance>;
  parameter$: Observable<Parameter>;

  constructor(route: ActivatedRoute, yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.parameter$ = yamcs.getSelectedInstance().getParameter(qualifiedName);
  }
}
