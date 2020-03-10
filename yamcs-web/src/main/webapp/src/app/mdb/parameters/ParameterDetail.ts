import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { EnumValue, Parameter } from '../../client';

@Component({
  selector: 'app-parameter-detail',
  templateUrl: './ParameterDetail.html',
  styleUrls: ['./ParameterDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterDetail {

  @Input()
  instance: string;

  @Input()
  parameter: Parameter;

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
