import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subscription } from 'rxjs';
import { Instance, Parameter, ParameterValue } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ParameterSummaryTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterSummaryTab {

  instance: Instance;
  parameter$ = new BehaviorSubject<Parameter | null>(null);

  parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  parameterValueSubscription: Subscription;

  constructor(route: ActivatedRoute, private yamcs: YamcsService) {
    this.instance = yamcs.getInstance();

    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.parent!.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeParameter(qualifiedName);
    });
  }

  changeParameter(qualifiedName: string) {
    this.yamcs.yamcsClient.getParameter(this.instance.name, qualifiedName).then(parameter => {
      this.parameter$.next(parameter);

      if (this.parameterValueSubscription) {
        this.parameterValueSubscription.unsubscribe();
      }
      this.yamcs.getInstanceClient()!.getParameterValueUpdates({
        id: [{ name: qualifiedName }],
        abortOnInvalid: false,
        sendFromCache: true,
        subscriptionId: -1,
        updateOnExpiration: true,
        useNumericIds: true,
      }).then(res => {
        this.parameterValueSubscription = res.parameterValues$.subscribe(pvals => {
          this.parameterValue$.next(pvals[0]);
        });
      });
    });
  }
}
