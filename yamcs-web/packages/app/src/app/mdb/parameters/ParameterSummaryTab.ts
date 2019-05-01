import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { EnumValue, Instance, Parameter, ParameterValue } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  templateUrl: './ParameterSummaryTab.html',
  styleUrls: ['./ParameterSummaryTab.css'],
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
    this.yamcs.getInstanceClient()!.getParameter(qualifiedName).then(parameter => {
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

  getDefaultAlarmLevel(parameter: Parameter, enumValue: EnumValue) {
    if (parameter.type && parameter.type.defaultAlarm) {
      const alarm = parameter.type.defaultAlarm;
      if (alarm.enumerationAlarm) {
        for (const enumAlarm of alarm.enumerationAlarm) {
          if (enumAlarm.label === enumValue.label) {
            return enumAlarm.level;
          }
        }
      }
    }
  }
}
