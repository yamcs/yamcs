import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AcknowledgeAlarmDialog } from './AcknowledgeAlarmDialog';
import { AlarmDetail } from './AlarmDetail';
import { AlarmsRoutingModule, routingComponents } from './AlarmsRoutingModule';
import { AlarmsTable } from './AlarmsTable';
import { AlarmStateIcon } from './AlarmStateIcon';
import { ShelveAlarmDialog } from './ShelveAlarmDialog';

@NgModule({
  imports: [
    SharedModule,
    AlarmsRoutingModule,
  ],
  declarations: [
    routingComponents,
    AcknowledgeAlarmDialog,
    AlarmDetail,
    AlarmStateIcon,
    AlarmsTable,
    ShelveAlarmDialog,
  ],
})
export class AlarmsModule {
}
