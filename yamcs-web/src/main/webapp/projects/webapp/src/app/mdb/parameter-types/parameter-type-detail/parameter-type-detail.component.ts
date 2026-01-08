import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import {
  ContextAlarmInfo,
  ParameterType,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { ExpressionComponent } from '../../../shared/expression/expression.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { ParameterCalibrationComponent } from '../../parameters/parameter-calibration/parameter-calibration.component';

@Component({
  selector: 'app-parameter-type-detail',
  templateUrl: './parameter-type-detail.component.html',
  styleUrl: './parameter-type-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ExpressionComponent,
    MarkdownComponent,
    ParameterCalibrationComponent,
    WebappSdkModule,
  ],
})
export class ParameterTypeDetailComponent {
  @Input()
  parameterType: ParameterType;

  constructor(readonly yamcs: YamcsService) {}

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
