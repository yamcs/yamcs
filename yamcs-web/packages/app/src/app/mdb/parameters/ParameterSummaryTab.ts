import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Parameter, Instance, ParameterValue, EnumValue, AlarmRange } from '@yamcs/client';
import { ActivatedRoute } from '@angular/router';
import { YamcsService } from '../../core/services/YamcsService';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Subscription } from 'rxjs/Subscription';

@Component({
  templateUrl: './ParameterSummaryTab.html',
  styleUrls: ['./ParameterSummaryTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterSummaryTab {

  instance: Instance;
  parameter$: Promise<Parameter>;

  parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  parameterValueSubscription: Subscription;

  constructor(route: ActivatedRoute, yamcs: YamcsService) {
    this.instance = yamcs.getInstance();
    const qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.parameter$ = yamcs.getInstanceClient()!.getParameter(qualifiedName);

    yamcs.getInstanceClient()!.getParameterValueUpdates({
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

  describeRange(range: AlarmRange) {
    let result = '(-∞';
    if (range.minInclusive !== undefined) {
      result = '[' + range.minInclusive;
    } else if (range.minExclusive !== undefined) {
      result = '(' + range.minExclusive;
    }

    result += ', ';

    if (range.maxInclusive !== undefined) {
      result += range.maxInclusive + ']';
    } else if (range.maxExclusive !== undefined) {
      result += range.maxExclusive + ')';
    } else {
      result += '+∞)';
    }

    return result;
  }
}
