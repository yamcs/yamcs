import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { ContextAlarmInfo, EnumValue, Parameter, ParameterMember, ParameterType, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { AlarmLevelComponent } from '../../../shared/alarm-level/alarm-level.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { ParameterCalibrationComponent } from '../parameter-calibration/parameter-calibration.component';

@Component({
  standalone: true,
  selector: 'app-parameter-detail',
  templateUrl: './parameter-detail.component.html',
  styleUrl: './parameter-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AlarmLevelComponent,
    MarkdownComponent,
    ParameterCalibrationComponent,
    WebappSdkModule,
  ],
})
export class ParameterDetailComponent implements OnChanges {

  @Input()
  parameter: Parameter;

  @Input()
  offset: string;

  // A Parameter or a Member depending on whether the user is visiting
  // nested entries of an aggregate or array.
  entry$ = new BehaviorSubject<Parameter | ParameterMember | null>(null);

  constructor(readonly yamcs: YamcsService) {
  }

  ngOnChanges() {
    if (this.parameter) {
      if (this.offset) {
        const entry = utils.getEntryForOffset(this.parameter, this.offset);
        this.entry$.next(entry);
      } else {
        this.entry$.next(this.parameter);
      }
    } else {
      this.entry$.next(null);
    }
  }

  getDefaultAlarmLevel(ptype: ParameterType, enumValue: EnumValue) {
    if (ptype && ptype.defaultAlarm) {
      const alarm = ptype.defaultAlarm;
      if (alarm.enumerationAlarms) {
        for (const enumAlarm of alarm.enumerationAlarms) {
          if (enumAlarm.label === enumValue.label) {
            return enumAlarm.level;
          }
        }
      }
    }
  }

  getEnumerationAlarmLevel(contextAlarm: ContextAlarmInfo, enumValue: EnumValue) {
    const alarm = contextAlarm.alarm;
    for (const enumAlarm of alarm.enumerationAlarms) {
      if (enumAlarm.label === enumValue.label) {
        return enumAlarm.level;
      }
    }
  }
}
