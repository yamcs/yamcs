import { Component, ChangeDetectionStrategy, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance, Parameter, ParameterValue } from '@yamcs/client';
import { Observable } from 'rxjs/Observable';
import { Store } from '@ngrx/store';
import { State } from '../../app.reducers';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { YamcsService } from '../../core/services/YamcsService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Subscription } from 'rxjs/Subscription';

@Component({
  templateUrl: './ParameterPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterPage implements OnDestroy {

  instance$: Observable<Instance>;
  parameter$: Promise<Parameter>;

  parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  parameterValueSubscription: Subscription;

  constructor(route: ActivatedRoute, yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    this.parameter$ = yamcs.getSelectedInstance().getParameter(qualifiedName);

    yamcs.getSelectedInstance().getParameterValueUpdates({
      id: [{ name: qualifiedName }],
      abortOnInvalid: false,
      sendFromCache: true,
      subscriptionId: -1,
      updateOnExpiration: true,
    }).then(res => {
      this.parameterValueSubscription = res.parameterValues$.subscribe(pvals => {
        this.parameterValue$.next(pvals[0]);
      });
    });
  }

  ngOnDestroy() {
    if (this.parameterValueSubscription) {
      this.parameterValueSubscription.unsubscribe();
    }
  }
}
