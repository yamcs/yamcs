import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ContextAlarmInfo, EnumValue, Member, Parameter, ParameterType, ParameterValue } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { EntryForOffsetPipe } from '../../shared/pipes/EntryForOffsetPipe';
import { ParameterTypeForPathPipe } from '../../shared/pipes/ParameterTypeForPathPipe';

@Component({
  selector: 'app-parameter-detail',
  templateUrl: './ParameterDetail.html',
  styleUrls: ['./ParameterDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterDetail implements OnChanges {

  @Input()
  parameter: Parameter;

  @Input()
  offset: string;

  @Input()
  pval: ParameterValue;

  // A Parameter or a Member depending on whether the user is visiting
  // nested entries of an aggregate or array.
  entry$ = new BehaviorSubject<Parameter | Member | null>(null);
  ptype$ = new BehaviorSubject<ParameterType | null>(null);

  constructor(
    readonly yamcs: YamcsService,
    private entryForOffsetPipe: EntryForOffsetPipe,
    private parameterTypeForPathPipe: ParameterTypeForPathPipe) {
  }

  ngOnChanges() {
    if (this.parameter) {
      if (this.offset) {
        const entry = this.entryForOffsetPipe.transform(this.parameter, this.offset);
        this.entry$.next(entry);
      } else {
        this.entry$.next(this.parameter);
      }
      this.ptype$.next(this.parameterTypeForPathPipe.transform(this.parameter) || null);
    } else {
      this.entry$.next(null);
      this.ptype$.next(null);
    }
  }

  getDefaultAlarmLevel(ptype: ParameterType, enumValue: EnumValue) {
    if (ptype && ptype.defaultAlarm) {
      const alarm = ptype.defaultAlarm;
      if (alarm.enumerationAlarm) {
        for (const enumAlarm of alarm.enumerationAlarm) {
          if (enumAlarm.label === enumValue.label) {
            return enumAlarm.level;
          }
        }
      }
    }
  }

  getEnumerationAlarmLevel(contextAlarm: ContextAlarmInfo, enumValue: EnumValue) {
    const alarm = contextAlarm.alarm;
    for (const enumAlarm of alarm.enumerationAlarm) {
      if (enumAlarm.label === enumValue.label) {
        return enumAlarm.level;
      }
    }
  }
}
