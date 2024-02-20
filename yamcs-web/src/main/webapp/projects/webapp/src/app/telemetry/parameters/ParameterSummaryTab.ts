import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Parameter, ParameterSubscription, ParameterValue, Synchronizer, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';

@Component({
  templateUrl: './ParameterSummaryTab.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterSummaryTab implements OnDestroy {

  parameter$ = new BehaviorSubject<Parameter | null>(null);
  offset$ = new BehaviorSubject<string | null>(null);

  private parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  private parameterValueSubscription: ParameterSubscription;

  private dirty = false;
  private syncSubscription: Subscription;
  pval$ = new BehaviorSubject<ParameterValue | null>(null);

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService, private synchronizer: Synchronizer) {

    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.parent!.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeParameter(qualifiedName);
    });

    // Extra step to slow down UI updates
    this.syncSubscription = this.synchronizer.syncFast(() => {
      if (this.dirty) {
        this.pval$.next(this.parameterValue$.value);
        this.dirty = false;
      }
    });
  }

  changeParameter(qualifiedName: string) {
    this.yamcs.yamcsClient.getParameter(this.yamcs.instance!, qualifiedName).then(parameter => {
      this.parameter$.next(parameter);

      if (qualifiedName !== parameter.qualifiedName) {
        this.offset$.next(qualifiedName.substring(parameter.qualifiedName.length));
      } else {
        this.offset$.next(null);
      }

      if (this.parameterValueSubscription) {
        this.parameterValueSubscription.cancel();
      }
      this.parameterValue$.next(null);
      this.pval$.next(null);
      this.parameterValueSubscription = this.yamcs.yamcsClient.createParameterSubscription({
        instance: this.yamcs.instance!,
        processor: this.yamcs.processor!,
        id: [{ name: qualifiedName }],
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
        action: 'REPLACE',
      }, data => {
        this.parameterValue$.next(data.values ? data.values[0] : null);
        if (this.pval$.value == null) {
          this.pval$.next(this.parameterValue$.value);
        } else {
          this.dirty = true;
        }
      });
    });
  }

  ngOnDestroy() {
    this.parameterValueSubscription?.cancel();
    this.syncSubscription?.unsubscribe();
  }
}
