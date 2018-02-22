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

@NgModule({
  imports: [
    SharedModule,
    SystemRoutingModule,
  ],
  declarations: [
    routingComponents,
    CommandQueuesTable,
    HexComponent,
    QueuedCommandsTable,
    RecordComponent,
    SystemPageTemplate,
    SystemToolbar,
    TmStatsTable,
  ]
})
export class SystemModule {
}
