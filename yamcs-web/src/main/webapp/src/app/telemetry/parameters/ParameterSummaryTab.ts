import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { Instance, Parameter, ParameterSubscription, ParameterValue } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ParameterSummaryTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterSummaryTab implements OnDestroy {

  instance: Instance;
  parameter$ = new BehaviorSubject<Parameter | null>(null);

  parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  parameterValueSubscription: ParameterSubscription;

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
        this.parameterValueSubscription.cancel();
      }
      this.parameterValueSubscription = this.yamcs.yamcsClient.createParameterSubscription({
        instance: this.instance.name,
        processor: this.yamcs.getProcessor().name,
        id: [{ name: qualifiedName }],
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
        action: 'REPLACE',
      }, data => {
        this.parameterValue$.next(data.values[0]);
      });
    });
  }

  ngOnDestroy() {
    if (this.parameterValueSubscription) {
      this.parameterValueSubscription.cancel();
    }
  }
}
