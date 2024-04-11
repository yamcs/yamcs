import { ChangeDetectionStrategy, Component, OnChanges, OnDestroy, input } from '@angular/core';
import { Parameter, ParameterSubscription, ParameterValue, Synchronizer, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { ParameterDetailComponent } from '../parameter-detail/parameter-detail.component';

@Component({
  standalone: true,
  templateUrl: './parameter-summary-tab.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ParameterDetailComponent,
    WebappSdkModule,
  ],
})
export class ParameterSummaryTabComponent implements OnChanges, OnDestroy {

  qualifiedName = input.required<string>({ alias: 'parameter' });

  parameter$ = new BehaviorSubject<Parameter | null>(null);
  offset$ = new BehaviorSubject<string | null>(null);

  private parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  private parameterValueSubscription: ParameterSubscription;

  private dirty = false;
  private syncSubscription: Subscription;
  pval$ = new BehaviorSubject<ParameterValue | null>(null);

  constructor(readonly yamcs: YamcsService, private synchronizer: Synchronizer) {
    this.syncSubscription = this.synchronizer.syncFast(() => {
      if (this.dirty) {
        this.pval$.next(this.parameterValue$.value);
        this.dirty = false;
      }
    });
  }

  ngOnChanges() {
    const qualifiedName = this.qualifiedName();
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
