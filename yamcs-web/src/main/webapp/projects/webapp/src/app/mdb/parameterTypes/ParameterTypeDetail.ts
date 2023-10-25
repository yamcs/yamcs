import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { ContextAlarmInfo, EnumValue, ParameterType, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-parameter-type-detail',
  templateUrl: './ParameterTypeDetail.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterTypeDetail {

  @Input()
  parameterType: ParameterType;

  constructor(readonly yamcs: YamcsService) {
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
