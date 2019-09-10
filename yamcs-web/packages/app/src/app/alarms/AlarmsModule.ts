import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AcknowledgeAlarmDialog } from './AcknowledgeAlarmDialog';
import { AlarmDetail } from './AlarmDetail';
import { AlarmsRoutingModule, routingComponents } from './AlarmsRoutingModule';
import { AlarmsTable } from './AlarmsTable';
import { AlarmStateIcon } from './AlarmStateIcon';
import { ShelveAlarmDialog } from './ShelveAlarmDialog';
import { ShortNamePipe } from './ShortNamePipe';

const dialogComponents = [
  AcknowledgeAlarmDialog,
  ShelveAlarmDialog,
];

const pipes = [
  ShortNamePipe,
];

@NgModule({
  imports: [
    SharedModule,
    AlarmsRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    pipes,
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
