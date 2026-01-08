import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
} from '@angular/core';
import {
  ContextAlarmInfo,
  Parameter,
  ParameterMember,
  ParameterType,
  ParameterValue,
  WebappSdkModule,
  YamcsService,
  utils,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { ExpressionComponent } from '../../../shared/expression/expression.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { SeverityMeterComponent } from '../severity-meter/severity-meter.component';

@Component({
  selector: 'app-parameter-detail',
  templateUrl: './parameter-detail.component.html',
  styleUrl: './parameter-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ExpressionComponent,
    MarkdownComponent,
    SeverityMeterComponent,
    WebappSdkModule,
  ],
})
export class ParameterDetailComponent implements OnChanges {
  @Input()
  parameter: Parameter;

  @Input()
  offset: string;

  @Input()
  pval?: ParameterValue;

  // A Parameter or a Member depending on whether the user is visiting
  // nested entries of an aggregate or array.
  entry$ = new BehaviorSubject<Parameter | ParameterMember | null>(null);
  ptype$ = new BehaviorSubject<ParameterType | null>(null);

  constructor(readonly yamcs: YamcsService) {}

  ngOnChanges() {
    if (this.parameter) {
      if (this.offset) {
        const entry = utils.getEntryForOffset(this.parameter, this.offset);
        this.entry$.next(entry);
      } else {
        this.entry$.next(this.parameter);
      }
      this.ptype$.next(utils.getParameterTypeForPath(this.parameter) || null);
    } else {
      this.entry$.next(null);
      this.ptype$.next(null);
    }
  }

  getDefaultAlarmLevel(ptype: ParameterType, label: string) {
    if (ptype && ptype.defaultAlarm) {
      const alarm = ptype.defaultAlarm;
      if (alarm.enumerationAlarms) {
        for (const enumAlarm of alarm.enumerationAlarms) {
          if (enumAlarm.label === label) {
            return enumAlarm.level;
          }
        }
      }
      return alarm.defaultLevel;
    }
  }

  getEnumerationAlarmLevel(contextAlarm: ContextAlarmInfo, label: string) {
    const alarm = contextAlarm.alarm;
    for (const enumAlarm of alarm.enumerationAlarms) {
      if (enumAlarm.label === label) {
        return enumAlarm.level;
      }
    }
    return alarm.defaultLevel;
  }
}
