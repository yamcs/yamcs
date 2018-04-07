import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { SystemRoutingModule, routingComponents } from './SystemRoutingModule';
import { SystemToolbar } from './template/SystemToolbar';
import { SystemPageTemplate } from './template/SystemPageTemplate';
import { RecordComponent } from './table/RecordComponent';
import { HexComponent } from './table/HexComponent';
import { TmStatsTable } from './processors/TmStatsTable';
import { QueuedCommandsTable } from './processors/QueuedCommandsTable';
import { CommandQueuesTable } from './processors/CommandQueuesTable';
import { ShowEnumDialog } from './table/ShowEnumDialog';

const dialogComponents = [
  ShowEnumDialog,
];

@NgModule({
  imports: [
    SharedModule,
    SystemRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    CommandQueuesTable,
    HexComponent,
    QueuedCommandsTable,
    RecordComponent,
    SystemPageTemplate,
    SystemToolbar,
    TmStatsTable,
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class SystemModule {
}
