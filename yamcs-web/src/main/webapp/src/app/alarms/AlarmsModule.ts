import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AcknowledgeAlarmDialog } from './AcknowledgeAlarmDialog';
import { AlarmDetail } from './AlarmDetail';
import { AlarmsRoutingModule, routingComponents } from './AlarmsRoutingModule';
import { AlarmsTable } from './AlarmsTable';
import { AlarmStateIcon } from './AlarmStateIcon';
import { ShelveAlarmDialog } from './ShelveAlarmDialog';

const dialogComponents = [
  AcknowledgeAlarmDialog,
  ShelveAlarmDialog,
];

@NgModule({
  imports: [
    SharedModule,
    AlarmsRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    AlarmDetail,
    AlarmStateIcon,
    AlarmsTable,
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class AlarmsModule {
}
