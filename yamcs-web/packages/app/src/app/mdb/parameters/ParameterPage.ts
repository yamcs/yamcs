import { Component, ChangeDetectionStrategy, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Instance, Parameter, ParameterValue } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Subscription } from 'rxjs/Subscription';
import { Title } from '@angular/platform-browser';
import { ValuePipe } from '../../shared/pipes/ValuePipe';
import { UnitsPipe } from '../../shared/pipes/UnitsPipe';

@Component({
  templateUrl: './ParameterPage.html',
  styleUrls: ['./ParameterPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterPage implements OnDestroy {

  instance: Instance;
  parameter$ = new BehaviorSubject<Parameter | null>(null);

  parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  parameterValueSubscription: Subscription;

  constructor(
    route: ActivatedRoute,
    yamcs: YamcsService,
    private title: Title,
    private valuePipe: ValuePipe,
    private unitsPipe: UnitsPipe,
  ) {
    this.instance = yamcs.getInstance();

    const qualifiedName = route.snapshot.paramMap.get('qualifiedName')!;
    yamcs.getInstanceClient()!.getParameter(qualifiedName).then(parameter => {
      this.parameter$.next(parameter);
      this.updateTitle();
    });

    yamcs.getInstanceClient()!.getParameterValueUpdates({
      id: [{ name: qualifiedName }],
      abortOnInvalid: false,
      sendFromCache: true,
      subscriptionId: -1,
      updateOnExpiration: true,
    }).then(res => {
      this.parameterValueSubscription = res.parameterValues$.subscribe(pvals => {
        this.parameterValue$.next(pvals[0]);
        this.updateTitle();
      });
    });
  }

  updateTitle() {
    const parameter = this.parameter$.getValue();
    if (parameter) {
      let title = parameter.name;
      const pval = this.parameterValue$.getValue();
      if (pval) {
        title += ': ' + this.valuePipe.transform(pval.engValue);
        if (parameter.type && parameter.type.unitSet) {
          title += ' ' + this.unitsPipe.transform(parameter.type.unitSet);
        }
        if (pval.rangeCondition && pval.rangeCondition === 'LOW') {
          title += ' ↓';
        } else if (pval.rangeCondition && pval.rangeCondition === 'HIGH') {
          title += ' ↑';
        }
      }
      this.title.setTitle(title + ' - Yamcs');
    }
  }

  ngOnDestroy() {
    if (this.parameterValueSubscription) {
      this.parameterValueSubscription.unsubscribe();
    }
  }
}
